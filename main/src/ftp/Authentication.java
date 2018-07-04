package ftp;

class Authentication {

    Authentication() {
    }

    boolean isValidUser(String user, String pass) {
        String fileAuth = System.getProperty("ftp.user." + user);
        return fileAuth != null && pass.equals(fileAuth);

    }

}
