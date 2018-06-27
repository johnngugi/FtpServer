package ftp;

import java.io.File;
import java.io.IOException;
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

    private boolean isUTF8Enable = false;

    private SimpleDateFormat fmtDate = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
    private SimpleDateFormat fmtPast = new SimpleDateFormat("MMM dd  yyyy", Locale.ENGLISH);
    private SimpleDateFormat fmtStamp = new SimpleDateFormat("yyyyMMddHHmmss");

    private String[] extensions = new String[] {
            FtpUtil.FTP_COMMAND_AUTH, FtpUtil.FTP_COMMAND_PASV
    };

    RequestHandler(SocketChannel socket, String directory) {
        this.socket = socket;
        this.directory = directory;
        processFunctions.put(FtpUtil.FTP_COMMAND_USER, this::processUser);
        processFunctions.put(FtpUtil.FTP_COMMAND_PASS, this::processPassword);
        processFunctions.put(FtpUtil.FTP_COMMAND_AUTH, this::processSecurityExtension);
        processFunctions.put(FtpUtil.FTP_COMMAND_PWD, this::processPrintWorkingDirecory);
        processFunctions.put(FtpUtil.FTP_COMMAND_TYPE, this::processType);
        processFunctions.put(FtpUtil.FTP_COMMAND_PASV, this::processPassive);
        processFunctions.put(FtpUtil.FTP_COMMAND_LIST, this::processList);
        processFunctions.put(FtpUtil.FTP_COMMAND_CWD, this::processChangeWorkingDirectory);
        processFunctions.put(FtpUtil.FTP_COMMAND_CDUP, this::processChangeDirectoryUp);
        processFunctions.put(FtpUtil.FTP_COMMAND_SYST, this::processSystem);
        processFunctions.put(FtpUtil.FTP_COMMAND_FEAT, this::processFeatureList);
        processFunctions.put(FtpUtil.FTP_COMMAND_RETR, this::processRetrieve);
    }

    public void processCommand(String command, String parameter) throws IOException {
        command = command.toUpperCase();
        try {
            processFunctions.get(command).accept(parameter);
        } catch (NullPointerException e) {
            FtpUtil.println(socket, "502 " + command + " not implemented");
            e.printStackTrace();
        }
    }

    private void processFeatureList(String parameter) {
        StringBuilder sb = new StringBuilder();
        sb.append("211 Extensions supported:\r\n");
        for (String extension: extensions) {
            sb.append(extension).append("\r\n");
        }
        sb.append("211 End.\r\n");
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
            FtpUtil.println(socket, "215 " + System.getProperty("os.name"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processChangeDirectoryUp(String parameter) {
        processChangeWorkingDirectory("..");
    }

    private void processRetrieve(String parameter) {
        // todo process retrieve
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
        isUTF8Enable = true; // todo change this
        File[] files = userCurrent.listFiles();
        System.out.println(Arrays.toString(files));
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
                }

                sb.append(' ');
                sb.append(String.format("%3d", 1));
                sb.append(' ');
                sb.append(String.format("%-8s", this.userName));
                sb.append(' ');
                sb.append(String.format("%-8s", this.userName));
                sb.append(' ');
                long len = f.length();
                System.out.println("Length: " + len);
                if (f.isDirectory())
                    len = 4096;
                sb.append(String.format("%8d", len));
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
            System.out.println("LIST result " + sb.toString());

            try {
                if (data != null) {
                    FtpUtil.println(socket, "150 Opening ASCII mode data connection for file list");
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

    private void processPrintWorkingDirecory(String parameter) {
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
            if (parameter.equals("Anonymous")) {
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
                FtpUtil.println(socket, "226 Transfer complete.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
