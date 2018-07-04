package ftp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class RequestHandler implements DataConnectionListener {

    private final SocketChannel socket;
    private final String directory;
    private String userName;
    private Map<String, Consumer<String>> processFunctions = new HashMap<>();
    private boolean isBinary;
    private DataConnection data;
    private long restart;
    private File userCurrent = null;
    private File userRoot = null;

    private boolean isUTF8Enable = true;

    private SimpleDateFormat fmtDate = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
    private SimpleDateFormat fmtPast = new SimpleDateFormat("MMM dd  yyyy", Locale.ENGLISH);
    private SimpleDateFormat fmtStamp = new SimpleDateFormat("yyyyMMddHHmmss");

    private String[] extensions = new String[]{
            FtpUtil.FTP_COMMAND_AUTH, FtpUtil.FTP_COMMAND_PASV, "UTF8"
    };

    RequestHandler(SocketChannel socket, String directory) {
        this.socket = socket;
        this.directory = directory;
        processFunctions.put(FtpUtil.FTP_COMMAND_USER, this::processUser);
        processFunctions.put(FtpUtil.FTP_COMMAND_PASS, this::processPassword);
        processFunctions.put(FtpUtil.FTP_COMMAND_AUTH, this::processSecurityExtension);
        processFunctions.put(FtpUtil.FTP_COMMAND_PWD, this::processPrintWorkingDirectory);
        processFunctions.put(FtpUtil.FTP_COMMAND_TYPE, this::processType);
        processFunctions.put(FtpUtil.FTP_COMMAND_PASV, this::processPassive);
        processFunctions.put(FtpUtil.FTP_COMMAND_LIST, this::processList);
        processFunctions.put(FtpUtil.FTP_COMMAND_CWD, this::processChangeWorkingDirectory);
        processFunctions.put(FtpUtil.FTP_COMMAND_CDUP, this::processChangeDirectoryUp);
        processFunctions.put(FtpUtil.FTP_COMMAND_SYST, this::processSystem);
        processFunctions.put(FtpUtil.FTP_COMMAND_FEAT, this::processFeatureList);
        processFunctions.put(FtpUtil.FTP_COMMAND_OPTS, this::processOption);
        processFunctions.put(FtpUtil.FTP_COMMAND_RETR, this::processRetrieve);
        processFunctions.put(FtpUtil.FTP_COMMAND_REST, this::processFileReset);
        processFunctions.put(FtpUtil.FTP_COMMAND_STOR, this::processStore);
        processFunctions.put(FtpUtil.FTP_COMMAND_DELE, this::processDelete);
        processFunctions.put(FtpUtil.FTP_COMMAND_SIZE, this::processFileSize);
        processFunctions.put(FtpUtil.FTP_COMMAND_QUIT, this::processQuit);
        processFunctions.put(FtpUtil.FTP_COMMAND_PORT, this::processPortCommand);
    }

    void processCommand(String command, String parameter) throws IOException {
        command = command.toUpperCase();
        try {
            processFunctions.get(command).accept(parameter);
        } catch (NullPointerException e) {
            FtpUtil.println(socket, "502 " + command + " not implemented");
            e.printStackTrace();
        }
    }

    private void processPortCommand(String parameter) {
        String[] ports = parameter.split(",");
        if (data != null) {
            data.stop();
            data = null;
        }

        this.restart = 0L;

        InetSocketAddress addr = null;
        try {
            addr = new InetSocketAddress(
                    ports[0] + "." + ports[1] + "." + ports[2] + "." + ports[3],
                    Integer.parseInt(ports[4]) * 256 +
                            Integer.parseInt(ports[5]));

            FtpUtil.println(socket, "200 PORT command successful.");

            this.data = DataConnection.createActive(addr);
            this.data.setFileOffset(restart);
            this.data.addDataConnectionListener(this);
            this.data.start();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                FtpUtil.println(socket, "500 Invalid port format.");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void processQuit(String parameter) {
        try {
            FtpUtil.println(socket, "221 Goodbye.");
        } catch (IOException e) {
            System.out.println("Error quiting");
            e.printStackTrace();
        } finally {
            data.stop();
            FtpUtil.releaseChannelResource(socket);
        }
    }

    private void processFileSize(String parameter) {
        File f = null;
        if (parameter.charAt(0) == '/')
            f = new File(userRoot, parameter);
        else
            f = new File(userCurrent, parameter);

        try {
            if (f.exists()) {
                FtpUtil.println(socket, "213 " + f.length());
            } else {
                FtpUtil.println(socket, "550 " + parameter + ": No such file or directory");
            }
        } catch (IOException e) {
            System.out.println("Error processing SIZE command");
            e.printStackTrace();
        }
    }

    private void processDelete(String parameter) {
        File f = null;
        if (parameter.charAt(0) == '/')
            f = new File(userRoot, parameter);
        else
            f = new File(userCurrent, parameter);

        try {
            if (!f.exists()) {
                FtpUtil.println(socket, "521 " + parameter + ": No such directory.");
                return;
            }

            if (f.isFile() && f.delete()) {
                FtpUtil.println(socket, "250 DELE command successful.");
            } else {
                FtpUtil.println(socket, "521 Removing file was failed.");
            }
        } catch (IOException e) {
            System.out.println("Error processing DELE command");
            e.printStackTrace();
        }
    }

    private void processStore(String parameter) {
        File f = new File(userCurrent, parameter);

        try {
            if (data != null) {
                FtpUtil.println(socket, "150 Opening BINARY mode data connection for " + parameter);
                data.storeFile(f);
            } else {
                FtpUtil.println(socket, "552 Requested file action aborted.");
            }
        } catch (IOException e) {
            System.out.println("Error processing STOR command");
            e.printStackTrace();
        }
    }

    private void processFileReset(String parameter) {
        long offset = Long.parseLong(parameter);
        this.restart = offset;
        if (data != null) {
            data.setFileOffset(offset);
        }

        try {
            FtpUtil.println(socket, "350 Restarting at " + offset +
                    ". Send STORE or RETRIEVE to initiate transfer");
        } catch (IOException e) {
            System.out.println("Error processing REST command");
            e.printStackTrace();
        }
    }

    private void processOption(String parameter) {
        try {
            String[] params = parameter.split(" ");
            if (params.length > 1 && params[0].equalsIgnoreCase("UTF8")) {
                String flag = params[1].toUpperCase();
                isUTF8Enable = flag.equals("YES") || flag.equals("TRUE") || flag.equals("ON");

                FtpUtil.println(socket, "200 OPTS UTF8 command successful.");
            } else {
                FtpUtil.println(socket, "501 Syntax error in parameters or arguments.");
            }
        } catch (IOException e) {
            System.out.println("Error processing options");
            e.printStackTrace();
        }
    }

    private void processFeatureList(String parameter) {
        StringBuilder sb = new StringBuilder();
        sb.append("211-Extensions supported:\r\n");
        for (String extension : extensions) {
            String toAppend = " " + extension + "\r\n";
            sb.append(toAppend);
        }
        sb.append("211 End\r\n");
        System.out.println(sb.toString());
        try {
            FtpUtil.println(socket, sb.toString());
        } catch (IOException e) {
            System.out.println("Error sending feature list");
            e.printStackTrace();
        }
    }

    private void processSystem(String parameter) {
        try {
            FtpUtil.println(socket, "215 UNIX Type: L8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processChangeDirectoryUp(String parameter) {
        processChangeWorkingDirectory("..");
    }

    private void processRetrieve(String parameter) {
        File f = null;
        if (parameter.charAt(0) == '/')
            f = new File(userRoot, parameter);
        else
            f = new File(userCurrent, parameter);

        try {
            if (!f.exists()) {
                FtpUtil.println(socket, "550 " + parameter + ": No such file or directory");
                if (data != null)
                    data.stop();
                return;
            }

            if (data != null) {
                FtpUtil.println(socket, "150 Opening BINARY mode data connection for " +
                        parameter + " (" + f.length() + " bytes)");
                data.sendFile(f);
            } else {
                FtpUtil.println(socket, "552 Requested file action aborted.");
            }
        } catch (IOException e) {
            System.out.println("Error processing RETR command");
            e.printStackTrace();
        }
    }

    private void processChangeWorkingDirectory(String parameter) {
        File toChange = null;
        if (parameter.length() > 0 && parameter.charAt(0) == '/') {
            toChange = new File(userRoot, parameter.substring(1));
        } else {
            toChange = new File(userCurrent, parameter);
        }

        try {
            if (!toChange.exists() || !toChange.isDirectory()) {
                FtpUtil.println(socket, "550 " + parameter + ": No such file or directory");
                return;
            }

            String root = userRoot.getAbsolutePath();
            String willChange = toChange.getCanonicalPath();
            if (!willChange.startsWith(root)) {
                FtpUtil.println(socket, "553 Requested action not taken.");
                return;
            }

            this.userCurrent = new File(willChange);
            FtpUtil.println(socket, "250 CWD command successful");
        } catch (IOException e) {
            System.out.println("Problem processing CWD");
            e.printStackTrace();
        }
    }

    private void processList(String parameter) {
        File[] files = userCurrent.listFiles();
        StringBuilder sb = new StringBuilder();

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);

        List<File> list = null;
        if (files != null) {
            list = new ArrayList<>(files.length);
            Collections.addAll(list, files);

            list.sort(Comparator.comparing(File::getName));

            for (File f : list) {
                if (f.isDirectory()) {
                    sb.append("drwxr-xr-x");
                } else if (f.isFile()) {
                    sb.append("-rw-r--r--");
                } else continue;

                sb.append(' ');
                sb.append(String.format("%4d", 1));
                sb.append(' ');
                sb.append("user");
                sb.append(' ');
                sb.append("group");
                long len = f.length();
                if (f.isDirectory())
                    len = 4096;
                sb.append(String.format("%13d", len));
                sb.append(' ');

                cal.setTimeInMillis(f.lastModified());
                if (cal.get(Calendar.YEAR) == currentYear) {
                    sb.append(fmtDate.format(cal.getTime()));
                } else {
                    sb.append(fmtPast.format(cal.getTime()));
                }
                sb.append(' ');
                sb.append(f.getName());
                sb.append("\r\n");
            }
            System.out.println(sb.toString());

            try {
                if (data != null) {
                    FtpUtil.println(socket, "150 Opening ASCII mode data connection for file list.\r\n");
                    data.send(sb.toString(), isUTF8Enable);
                } else {
                    FtpUtil.println(socket, "552 Requested file list action aborted.");
                }
            } catch (IOException e) {
                System.out.println("Error processing LIST command");
                e.printStackTrace();
            }
        }
    }

    private void processPassive(String parameter) {
        if (data != null) {
            data.stop();
        }

        this.restart = 0L;

        try {
            data = DataConnection.createPassive();
            data.setFileOffset(restart);
            data.addDataConnectionListener(this);
            data.start();
            FtpUtil.println(socket, "227 Entering Passive Mode (" + data.getAddressAsString() + ")");
        } catch (IOException e) {
            System.out.println("Error setting passive mode");
            e.printStackTrace();
        }
    }

    private void processType(String parameter) {
        parameter = parameter.toUpperCase();

        try {
            switch (parameter) {
                case "I":
                    isBinary = true;
                    break;
                case "A":
                    isBinary = false;
                    break;
                default:
                    FtpUtil.println(socket, "504 Command not implemented for that parameter.");
                    return;
            }
            FtpUtil.println(socket, "200 Type set to " + parameter);
        } catch (IOException e) {
            System.out.println("Error processing type");
            e.printStackTrace();
        }
    }

    private void processPrintWorkingDirectory(String parameter) {
        try {
            String root = userRoot.getAbsolutePath();
            String curr = userCurrent.getAbsolutePath();

            curr = curr.substring(root.length());

            if (curr.length() == 0)
                curr = "/";

            FtpUtil.println(socket, "257 " + curr);
        } catch (IOException e) {
            System.out.println("Error occured with processing working directory");
            e.printStackTrace();
        }
    }

    private void processSecurityExtension(String parameter) {
        try {
            if (parameter.equals("TLS")) {
                FtpUtil.println(socket, "502 " + parameter + " not implemented");
            }

            if (parameter.equals("SSL")) {
                FtpUtil.println(socket, "502 " + parameter + " not implemented");
            }
        } catch (IOException e) {
            System.out.println("Error processing security extension");
            e.printStackTrace();
        }
    }

    private void processPassword(String parameter) {
        try {
            if (this.userName == null) {
                FtpUtil.println(socket, "503 Bad sequence of commands. Send USER first.");
            }

            userRoot = new File(this.directory);

            if (!userRoot.exists()) {
                System.out.println("Directory doesn't exist");
                System.exit(1);
//                userRoot.mkdirs();
            }

            userCurrent = userRoot;
            FtpUtil.println(socket, "230 User " + this.userName + " logged in.");
        } catch (IOException e) {
            System.out.println("Error processing password");
            e.printStackTrace();
        }
    }

    private void processUser(String parameter) {
        try {
            if (parameter.toLowerCase().equals("anonymous")) {
                this.userName = parameter;
                userRoot = new File(this.directory);

                if (!userRoot.exists()) {
                    System.out.println("Directory doesn't exist");
                    System.exit(1);
//                userRoot.mkdirs();
                }

                userCurrent = userRoot;
                FtpUtil.println(socket, "230 Anonymous user logged in");
//                FtpUtil.println(socket, "331 Password should be email address");
                return;
            }
            this.userName = parameter;
            FtpUtil.println(socket, "331 Password required for " + parameter);
        } catch (IOException e) {
            System.out.println("Error with processing user");
            e.printStackTrace();
        }
    }

    @Override
    public void actionNegotiated(boolean isOk) {
        System.out.println("* Event: actionNegotiated: " + isOk);
    }

    @Override
    public void transferStarted() {
        System.out.println("* Event: transferStarted");
    }

    @Override
    public void transferCompleted(boolean hasError) {
        System.out.println("* Event: transferCompleted: hasError=" + hasError);

        try {
            if (!hasError)
                FtpUtil.println(socket, "226 Transfer complete.\r\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
