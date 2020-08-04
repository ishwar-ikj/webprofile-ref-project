package no.steras.opensamlbook;

public class Utils {
    public static String getSamlResponseFromForm(String input) {
        String[] pairs = input.split("&");
        for (String pair : pairs) {
            if (!pair.startsWith("SAMLResponse")) {
                continue;
            }
            int idx = pair.indexOf("=");
            return pair.substring(idx + 1);
        }
        return null;
    }
}
