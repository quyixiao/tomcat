package com.luban;

public class TestUrlPattern {


    public static void main(String[] args) {
        System.out.println(validateURLPattern("*.xxx/"));
    }

    public static boolean validateURLPattern(String urlPattern) {

        if (urlPattern == null)
            return (false);
        if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
            return (false);
        }
        if (urlPattern.equals("")) {
            return true;
        }
        if (urlPattern.startsWith("*.")) {
            if (urlPattern.indexOf('/') < 0) {
                checkUnusualURLPattern(urlPattern);
                return (true);
            } else
                return (false);
        }
        if ( (urlPattern.startsWith("/")) &&
                (urlPattern.indexOf("*.") < 0)) {
            checkUnusualURLPattern(urlPattern);
            return (true);
        } else
            return (false);

    }

    /**
     * Check for unusual but valid <code>&lt;url-pattern&gt;</code>s.
     * See Bugzilla 34805, 43079 & 43080
     */
    public static void checkUnusualURLPattern(String urlPattern) {
        // First group checks for '*' or '/foo*' style patterns
        // Second group checks for *.foo.bar style patterns
        if((urlPattern.endsWith("*") && (urlPattern.length() < 2 ||
                urlPattern.charAt(urlPattern.length()-2) != '/')) ||
                urlPattern.startsWith("*.") && urlPattern.length() > 2 &&
                        urlPattern.lastIndexOf('.') > 1) {
            System.out.println("Suspicious url pattern: \"" + urlPattern + "\"" +
                    " in context [] - see" +
                    " sections 12.1 and 12.2 of the Servlet specification");
        }
    }

}
