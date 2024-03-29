/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * HTTP header value parser implementation. Parsing HTTP headers as per RFC2616
 * is not always as simple as it first appears. For headers that only use tokens
 * the simple approach will normally be sufficient. However, for the other
 * headers, while simple code meets 99.9% of cases, there are often some edge
 * cases that make things far more complicated.
 *
 * The purpose of this parser is to let the parser worry about the edge cases.
 * It provides tolerant (where safe to do so) parsing of HTTP header values
 * assuming that wrapped header lines have already been unwrapped. (The Tomcat
 * header processing code does the unwrapping.)
 *
 * Provides parsing of the following HTTP header values as per RFC 2616:
 * - Authorization for DIGEST authentication
 * - MediaType (used for Content-Type header)
 *
 * Support for additional headers will be provided as required.
 */
public class HttpParser {

    @SuppressWarnings("unused")  // Unused due to buggy client implementations
    private static final Integer FIELD_TYPE_TOKEN = Integer.valueOf(0);
    private static final Integer FIELD_TYPE_QUOTED_STRING = Integer.valueOf(1);
    private static final Integer FIELD_TYPE_TOKEN_OR_QUOTED_STRING = Integer.valueOf(2);
    private static final Integer FIELD_TYPE_LHEX = Integer.valueOf(3);
    private static final Integer FIELD_TYPE_QUOTED_TOKEN = Integer.valueOf(4);

    private static final Map<String,Integer> fieldTypes =
            new HashMap<String,Integer>();

    private static final StringManager sm = StringManager.getManager(HttpParser.class);

    private static final Log log = LogFactory.getLog(HttpParser.class);

    private static final int ARRAY_SIZE = 128;

    private static final boolean[] IS_CONTROL = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_SEPARATOR = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_TOKEN = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_HEX = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_HTTP_PROTOCOL = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_ALPHA = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_NUMERIC = new boolean[ARRAY_SIZE];
    private static final boolean[] REQUEST_TARGET_ALLOW = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_UNRESERVED = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_SUBDELIM = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_USERINFO = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_RELAXABLE = new boolean[ARRAY_SIZE];

    private static final HttpParser DEFAULT;


    static {
        // Digest field types.
        // Note: These are more relaxed than RFC2617. This adheres to the
        //       recommendation of RFC2616 that servers are tolerant of buggy
        //       clients when they can be so without ambiguity.
        fieldTypes.put("username", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("realm", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("nonce", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("digest-uri", FIELD_TYPE_QUOTED_STRING);
        // RFC2617 says response is <">32LHEX<">. 32LHEX will also be accepted
        fieldTypes.put("response", FIELD_TYPE_LHEX);
        // RFC2617 says algorithm is token. <">token<"> will also be accepted
        fieldTypes.put("algorithm", FIELD_TYPE_QUOTED_TOKEN);
        fieldTypes.put("cnonce", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("opaque", FIELD_TYPE_QUOTED_STRING);
        // RFC2617 says qop is token. <">token<"> will also be accepted
        fieldTypes.put("qop", FIELD_TYPE_QUOTED_TOKEN);
        // RFC2617 says nc is 8LHEX. <">8LHEX<"> will also be accepted
        fieldTypes.put("nc", FIELD_TYPE_LHEX);

        for (int i = 0; i < ARRAY_SIZE; i++) {
            // Control> 0-31, 127
            if (i < 32 || i == 127) {
                IS_CONTROL[i] = true;
            }

            // Separator
            if (    i == '(' || i == ')' || i == '<' || i == '>'  || i == '@'  ||
                    i == ',' || i == ';' || i == ':' || i == '\\' || i == '\"' ||
                    i == '/' || i == '[' || i == ']' || i == '?'  || i == '='  ||
                    i == '{' || i == '}' || i == ' ' || i == '\t') {
                IS_SEPARATOR[i] = true;
            }

            // Token: Anything 0-127 that is not a control and not a separator
            if (!IS_CONTROL[i] && !IS_SEPARATOR[i] && i < 128) {
                IS_TOKEN[i] = true;
            }

            // Hex: 0-9, a-f, A-F
            if ((i >= '0' && i <='9') || (i >= 'a' && i <= 'f') || (i >= 'A' && i <= 'F')) {
                IS_HEX[i] = true;
            }

            // Not valid for HTTP protocol
            // "HTTP/" DIGIT "." DIGIT
            if (i == 'H' || i == 'T' || i == 'P' || i == '/' || i == '.' || (i >= '0' && i <= '9')) {
                IS_HTTP_PROTOCOL[i] = true;
            }

            if (i >= '0' && i <= '9') {
                IS_NUMERIC[i] = true;
            }

            if (i >= 'a' && i <= 'z' || i >= 'A' && i <= 'Z') {
                IS_ALPHA[i] = true;
            }

            if (IS_ALPHA[i] || IS_NUMERIC[i] || i == '-' || i == '.' || i == '_' || i == '~') {
                IS_UNRESERVED[i] = true;
            }

            if (i == '!' || i == '$' || i == '&' || i == '\'' || i == '(' || i == ')' || i == '*' ||
                    i == '+' || i == ',' || i == ';' || i == '=') {
                IS_SUBDELIM[i] = true;
            }

            // userinfo    = *( unreserved / pct-encoded / sub-delims / ":" )
            if (IS_UNRESERVED[i] || i == '%' || IS_SUBDELIM[i] || i == ':') {
                IS_USERINFO[i] = true;
            }

            // The characters that are normally not permitted for which the
            // restrictions may be relaxed when used in the path and/or query
            // string
            if (i == '\"' || i == '<' || i == '>' || i == '[' || i == '\\' || i == ']' ||
                    i == '^' || i == '`'  || i == '{' || i == '|' || i == '}') {
                IS_RELAXABLE[i] = true;
            }
        }

        String prop = System.getProperty("tomcat.util.http.parser.HttpParser.requestTargetAllow");
        if (prop != null) {
            for (int i = 0; i < prop.length(); i++) {
                char c = prop.charAt(i);
                if (c == '{' || c == '}' || c == '|') {
                    REQUEST_TARGET_ALLOW[c] = true;
                } else {
                    log.warn(sm.getString("http.invalidRequestTargetCharacter",
                            Character.valueOf(c)));
                }
            }
        }

        DEFAULT = new HttpParser(null, null);
    }


    private final boolean[] IS_NOT_REQUEST_TARGET = new boolean[ARRAY_SIZE];
    private final boolean[] IS_ABSOLUTEPATH_RELAXED = new boolean[ARRAY_SIZE];
    private final boolean[] IS_QUERY_RELAXED = new boolean[ARRAY_SIZE];


    public HttpParser(String relaxedPathChars, String relaxedQueryChars) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            // Not valid for request target.
            // Combination of multiple rules from RFC7230 and RFC 3986. Must be
            // ASCII, no controls plus a few additional characters excluded
            if (IS_CONTROL[i] || i > 127 ||
                    i == ' ' || i == '\"' || i == '#' || i == '<' || i == '>' || i == '\\' ||
                    i == '^' || i == '`'  || i == '{' || i == '|' || i == '}') {
                if (!REQUEST_TARGET_ALLOW[i]) {
                    IS_NOT_REQUEST_TARGET[i] = true;
                }
            }

            /*
             * absolute-path  = 1*( "/" segment )
             * segment        = *pchar
             * pchar          = unreserved / pct-encoded / sub-delims / ":" / "@"
             *
             * Note pchar allows everything userinfo allows plus "@"
             */
            if (IS_USERINFO[i] || i == '@' || i == '/' || REQUEST_TARGET_ALLOW[i]) {
                IS_ABSOLUTEPATH_RELAXED[i] = true;
            }

            /*
             * query          = *( pchar / "/" / "?" )
             *
             * Note query allows everything absolute-path allows plus "?"
             */
            if (IS_ABSOLUTEPATH_RELAXED[i] || i == '?' || REQUEST_TARGET_ALLOW[i]) {
                IS_QUERY_RELAXED[i] = true;
            }
        }

        relax(IS_ABSOLUTEPATH_RELAXED, relaxedPathChars);
        relax(IS_QUERY_RELAXED, relaxedQueryChars);
    }

    /**
     * Parses an HTTP Authorization header for DIGEST authentication as per RFC
     * 2617 section 3.2.2.
     *
     * @param input The header value to parse
     *
     * @return  A map of directives and values as {@link String}s or
     *          <code>null</code> if a parsing error occurs. Although the
     *          values returned are {@link String}s they will have been
     *          validated to ensure that they conform to RFC 2617.
     *
     * @throws IllegalArgumentException If the header does not conform to RFC
     *                                  2617
     * @throws IOException If an error occurs while reading the input
     */
    public static Map<String,String> parseAuthorizationDigest (
            StringReader input) throws IllegalArgumentException, IOException {

        Map<String,String> result = new HashMap<String,String>();

        if (skipConstant(input, "Digest") != SkipResult.FOUND) {
            return null;
        }
        // All field names are valid tokens
        String field = readToken(input);
        if (field == null) {
            return null;
        }
        while (!field.equals("")) {
            if (skipConstant(input, "=") != SkipResult.FOUND) {
                return null;
            }
            String value = null;
            Integer type = fieldTypes.get(field.toLowerCase(Locale.ENGLISH));
            if (type == null) {
                // auth-param = token "=" ( token | quoted-string )
                type = FIELD_TYPE_TOKEN_OR_QUOTED_STRING;
            }
            switch (type.intValue()) {
                case 0:
                    // FIELD_TYPE_TOKEN
                    value = readToken(input);
                    break;
                case 1:
                    // FIELD_TYPE_QUOTED_STRING
                    value = readQuotedString(input, false);
                    break;
                case 2:
                    // FIELD_TYPE_TOKEN_OR_QUOTED_STRING
                    value = readTokenOrQuotedString(input, false);
                    break;
                case 3:
                    // FIELD_TYPE_LHEX
                    value = readLhex(input);
                    break;
                case 4:
                    // FIELD_TYPE_QUOTED_TOKEN
                    value = readQuotedToken(input);
                    break;
                default:
                    // Error
                    throw new IllegalArgumentException(
                            "TODO i18n: Unsupported type");
            }

            if (value == null) {
                return null;
            }
            result.put(field, value);

            if (skipConstant(input, ",") == SkipResult.NOT_FOUND) {
                return null;
            }
            field = readToken(input);
            if (field == null) {
                return null;
            }
        }

        return result;
    }

    public static MediaType parseMediaType(StringReader input)
            throws IOException {

        // Type (required)
        String type = readToken(input);
        if (type == null || type.length() == 0) {
            return null;
        }

        if (skipConstant(input, "/") == SkipResult.NOT_FOUND) {
            return null;
        }

        // Subtype (required)
        String subtype = readToken(input);
        if (subtype == null || subtype.length() == 0) {
            return null;
        }

        LinkedHashMap<String,String> parameters =
                new LinkedHashMap<String,String>();

        SkipResult lookForSemiColon = skipConstant(input, ";");
        if (lookForSemiColon == SkipResult.NOT_FOUND) {
            return null;
        }
        while (lookForSemiColon == SkipResult.FOUND) {
            String attribute = readToken(input);

            String value = "";
            if (skipConstant(input, "=") == SkipResult.FOUND) {
                value = readTokenOrQuotedString(input, true);
            }

            if (attribute != null) {
                parameters.put(attribute.toLowerCase(Locale.ENGLISH), value);
            }

            lookForSemiColon = skipConstant(input, ";");
            if (lookForSemiColon == SkipResult.NOT_FOUND) {
                return null;
            }
        }

        return new MediaType(type, subtype, parameters);
    }


    public boolean isNotRequestTargetRelaxed(int c) {
        // Fast for valid request target characters, slower for some incorrect
        // ones
        try {
            return IS_NOT_REQUEST_TARGET[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return true;
        }
    }


    public boolean isAbsolutePathRelaxed(int c) {
        // Fast for valid user info characters, slower for some incorrect
        // ones
        try {
            return IS_ABSOLUTEPATH_RELAXED[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public boolean isQueryRelaxed(int c) {
        // Fast for valid user info characters, slower for some incorrect
        // ones
        try {
            return IS_QUERY_RELAXED[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static String unquote(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }

        int start;
        int end;

        // Skip surrounding quotes if there are any
        if (input.charAt(0) == '"') {
            start = 1;
            end = input.length() - 1;
        } else {
            start = 0;
            end = input.length();
        }

        StringBuilder result = new StringBuilder();
        for (int i = start ; i < end; i++) {
            char c = input.charAt(i);
            if (input.charAt(i) == '\\') {
                i++;
                result.append(input.charAt(i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


    public static boolean isToken(int c) {
        // Fast for correct values, slower for incorrect ones
        try {
            return IS_TOKEN[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isHex(int c) {
        // Fast for correct values, slower for some incorrect ones
        try {
            return IS_HEX[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isNotRequestTarget(int c) {
        return DEFAULT.isNotRequestTargetRelaxed(c);
    }


    public static boolean isHttpProtocol(int c) {
        // Fast for valid HTTP protocol characters, slower for some incorrect
        // ones
        try {
            return IS_HTTP_PROTOCOL[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isAlpha(int c) {
        // Fast for valid alpha characters, slower for some incorrect
        // ones
        try {
            return IS_ALPHA[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isNumeric(int c) {
        // Fast for valid numeric characters, slower for some incorrect
        // ones
        try {
            return IS_NUMERIC[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isUserInfo(int c) {
        // Fast for valid user info characters, slower for some incorrect
        // ones
        try {
            return IS_USERINFO[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static void main(String[] args) {




    }




    public static void testIS_USERINFO(){
        for(byte i = 0 ;i < 127;i ++){
            if(!IS_USERINFO[i]){
                System.out.print((char)i + "=" + IS_USERINFO[i] + " " + i +" ");
            }
            if(i %8 ==0){
                System.out.println();
            }


        }

        System.out.println();
        for(byte i = 0 ;i < 127;i ++) {


            if (!IS_USERINFO[i]) {
                System.out.print(i + " ");
            }
        }
    }

    private static boolean isRelaxable(int c) {
        // Fast for valid user info characters, slower for some incorrect
        // ones
        try {
            return IS_RELAXABLE[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isAbsolutePath(int c) {
        return DEFAULT.isAbsolutePathRelaxed(c);
    }


    public static boolean isQuery(int c) {
        return DEFAULT.isQueryRelaxed(c);
    }


    // Skip any LWS and position to read the next character. The next character
    // is returned as being able to 'peek()' it allows a small optimisation in
    // some cases.
    private static int skipLws(Reader input) throws IOException {

        input.mark(1);
        int c = input.read();

        while (c == 32 || c == 9 || c == 10 || c == 13) {
            input.mark(1);
            c = input.read();
        }

        input.reset();
        return c;
    }

    static SkipResult skipConstant(Reader input, String constant)
            throws IOException {
        int len = constant.length();

        skipLws(input);
        input.mark(len);
        int c = input.read();

        for (int i = 0; i < len; i++) {
            if (i == 0 && c == -1) {
                return SkipResult.EOF;
            }
            if (c != constant.charAt(i)) {
                input.reset();
                return SkipResult.NOT_FOUND;
            }
            if (i != (len - 1)) {
                c = input.read();
            }
        }
        return SkipResult.FOUND;
    }

    /**
     * @return  the token if one was found, the empty string if no data was
     *          available to read or <code>null</code> if data other than a
     *          token was found
     */
    static String readToken(Reader input) throws IOException {
        StringBuilder result = new StringBuilder();

        skipLws(input);
        input.mark(1);
        int c = input.read();

        while (c != -1 && isToken(c)) {
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }
        // Use mark(1)/reset() rather than skip(-1) since skip() is a NOP
        // once the end of the String has been reached.
        input.reset();

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    /**
     * @return the quoted string if one was found, null if data other than a
     *         quoted string was found or null if the end of data was reached
     *         before the quoted string was terminated
     */
    private static String readQuotedString(Reader input, boolean returnQuoted) throws IOException {

        skipLws(input);
        int c = input.read();

        if (c != '"') {
            return null;
        }

        StringBuilder result = new StringBuilder();
        if (returnQuoted) {
            result.append('\"');
        }
        c = input.read();

        while (c != '"') {
            if (c == -1) {
                return null;
            } else if (c == '\\') {
                c = input.read();
                if (returnQuoted) {
                    result.append('\\');
                }
                result.append((char) c);
            } else {
                result.append((char) c);
            }
            c = input.read();
        }
        if (returnQuoted) {
            result.append('\"');
        }

        return result.toString();
    }

    private static String readTokenOrQuotedString(Reader input, boolean returnQuoted)
            throws IOException {

        // Peek at next character to enable correct method to be called
        int c = skipLws(input);

        if (c == '"') {
            return readQuotedString(input, returnQuoted);
        } else {
            return readToken(input);
        }
    }

    /**
     * Token can be read unambiguously with or without surrounding quotes so
     * this parsing method for token permits optional surrounding double quotes.
     * This is not defined in any RFC. It is a special case to handle data from
     * buggy clients (known buggy clients for DIGEST auth include Microsoft IE 8
     * &amp; 9, Apple Safari for OSX and iOS) that add quotes to values that
     * should be tokens.
     *
     * @return the token if one was found, null if data other than a token or
     *         quoted token was found or null if the end of data was reached
     *         before a quoted token was terminated
     */
    private static String readQuotedToken(Reader input) throws IOException {

        StringBuilder result = new StringBuilder();
        boolean quoted = false;

        skipLws(input);
        input.mark(1);
        int c = input.read();

        if (c == '"') {
            quoted = true;
        } else if (c == -1 || !isToken(c)) {
            return null;
        } else {
            result.append((char) c);
        }
        input.mark(1);
        c = input.read();

        while (c != -1 && isToken(c)) {
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }

        if (quoted) {
            if (c != '"') {
                return null;
            }
        } else {
            // Use mark(1)/reset() rather than skip(-1) since skip() is a NOP
            // once the end of the String has been reached.
            input.reset();
        }

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    /**
     * LHEX can be read unambiguously with or without surrounding quotes so this
     * parsing method for LHEX permits optional surrounding double quotes. Some
     * buggy clients (libwww-perl for DIGEST auth) are known to send quoted LHEX
     * when the specification requires just LHEX.
     *
     * <p>
     * LHEX are, literally, lower-case hexadecimal digits. This implementation
     * allows for upper-case digits as well, converting the returned value to
     * lower-case.
     *
     * @return  the sequence of LHEX (minus any surrounding quotes) if any was
     *          found, or <code>null</code> if data other LHEX was found
     */
    private static String readLhex(Reader input) throws IOException {

        StringBuilder result = new StringBuilder();
        boolean quoted = false;

        skipLws(input);
        input.mark(1);
        int c = input.read();

        if (c == '"') {
            quoted = true;
        } else if (c == -1 || !isHex(c)) {
            return null;
        } else {
            if ('A' <= c && c <= 'F') {
                c -= ('A' - 'a');
            }
            result.append((char) c);
        }
        input.mark(1);
        c = input.read();

        while (c != -1 && isHex(c)) {
            if ('A' <= c && c <= 'F') {
                c -= ('A' - 'a');
            }
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }

        if (quoted) {
            if (c != '"') {
                return null;
            }
        } else {
            // Use mark(1)/reset() rather than skip(-1) since skip() is a NOP
            // once the end of the String has been reached.
            input.reset();
        }

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    static double readWeight(Reader input, char delimiter) throws IOException {
        skipLws(input);
        int c = input.read();
        if (c == -1 || c == delimiter) {
            // No q value just whitespace
            return 1;
        } else if (c != 'q') {
            // Malformed. Use quality of zero so it is dropped.
            skipUntil(input, c, delimiter);
            return 0;
        }
        // RFC 7231 does not allow whitespace here but be tolerant
        skipLws(input);
        c = input.read();
        if (c != '=') {
            // Malformed. Use quality of zero so it is dropped.
            skipUntil(input, c, delimiter);
            return 0;
        }

        // RFC 7231 does not allow whitespace here but be tolerant
        skipLws(input);
        c = input.read();

        // Should be no more than 3 decimal places
        StringBuilder value = new StringBuilder(5);
        int decimalPlacesRead = -1;

        if (c == '0' || c == '1') {
            value.append((char) c);
            c = input.read();

            while (true) {
                if (decimalPlacesRead == -1 && c == '.') {
                    value.append('.');
                    decimalPlacesRead = 0;
                } else if (decimalPlacesRead > -1 && c >= '0' && c <= '9') {
                    if (decimalPlacesRead < 3) {
                        value.append((char) c);
                        decimalPlacesRead++;
                    }
                } else {
                    break;
                }
                c = input.read();
            }
        } else {
            // Malformed. Use quality of zero so it is dropped and skip until
            // EOF or the next delimiter
            skipUntil(input, c, delimiter);
            return 0;
        }

        if (c == 9 || c == 32) {
            skipLws(input);
            c = input.read();
        }

        // Must be at delimiter or EOF
        if (c != delimiter && c != -1) {
            // Malformed. Use quality of zero so it is dropped and skip until
            // EOF or the next delimiter
            skipUntil(input, c, delimiter);
            return 0;
        }

        double result = Double.parseDouble(value.toString());
        if (result > 1) {
            return 0;
        }
        return result;
    }


    static enum SkipResult {
        FOUND,
        NOT_FOUND,
        EOF
    }


    /**
     * @return If inIPv6 is false, the position of ':' that separates the host
     *         from the port or -1 if it is not present. If inIPv6 is true, the
     *         number of characters read
     */
    static int readHostIPv4(Reader reader, boolean inIPv6) throws IOException {
        int octet = -1;
        int octetCount = 1;
        int c;
        int pos = 0;

        // readAheadLimit doesn't matter as all the readers passed to this
        // method buffer the entire content.
        reader.mark(1);
        do {
            c = reader.read();
            if (c == '.') {
                if (octet > -1 && octet < 256) {
                    // Valid
                    octetCount++;
                    octet = -1;
                } else if (inIPv6 || octet == -1) {
                    throw new IllegalArgumentException(
                            sm.getString("http.invalidOctet", Integer.toString(octet)));
                } else {
                    // Might not be an IPv4 address. Could be a host / FQDN with
                    // a fully numeric component.
                    reader.reset();
                    return readHostDomainName(reader);
                }
            } else if (isNumeric(c)) {
                if (octet == -1) {
                    octet = c - '0';
                } else if (octet == 0) {
                    // Leading zero in non-zero octet. Not valid (ambiguous).
                    if (inIPv6) {
                        throw new IllegalArgumentException(sm.getString("http.invalidLeadingZero"));
                    } else {
                        // Could be a host/FQDN
                        reader.reset();
                        return readHostDomainName(reader);
                    }
                } else {
                    octet = octet * 10 + c - '0';
                }
            } else if (c == ':') {
                break;
            } else if (c == -1) {
                if (inIPv6) {
                    throw new IllegalArgumentException(sm.getString("http.noClosingBracket"));
                } else {
                    pos = -1;
                    break;
                }
            } else if (c == ']') {
                if (inIPv6) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException(sm.getString("http.closingBracket"));
                }
            } else if (!inIPv6 && (isAlpha(c) || c == '-')) {
                // Go back to the start and parse as a host / FQDN
                reader.reset();
                return readHostDomainName(reader);
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "http.illegalCharacterIpv4", Character.toString((char) c)));
            }
            pos++;
        } while (true);

        if (octetCount != 4 || octet < 0 || octet > 255) {
            // Might not be an IPv4 address. Could be a host name or a FQDN with
            // fully numeric components. Go back to the start and parse as a
            // host / FQDN.
            reader.reset();
            return readHostDomainName(reader);
        }

        return pos;
    }


    /**
     * @return The position of ':' that separates the host from the port or -1
     *         if it is not present
     */
    static int readHostIPv6(Reader reader) throws IOException {
        // Must start with '['
        int c = reader.read();
        if (c != '[') {
            throw new IllegalArgumentException(sm.getString("http.noOpeningBracket"));
        }

        int h16Count = 0;
        int h16Size = 0;
        int pos = 1;
        boolean parsedDoubleColon = false;
        int precedingColonsCount = 0;

        do {
            c = reader.read();
            if (h16Count == 0 && precedingColonsCount == 1 && c != ':') {
                // Can't start with a single :
                throw new IllegalArgumentException(sm.getString("http.singleColonStart"));
            }
            if (HttpParser.isHex(c)) {
                if (h16Size == 0) {
                    // Start of a new h16 block
                    precedingColonsCount = 0;
                    h16Count++;
                }
                h16Size++;
                if (h16Size > 4) {
                    throw new IllegalArgumentException(sm.getString("http.invalidHextet"));
                }
            } else if (c == ':') {
                if (precedingColonsCount >=2 ) {
                    // ::: is not allowed
                    throw new IllegalArgumentException(sm.getString("http.tooManyColons"));
                } else {
                    if(precedingColonsCount == 1) {
                        // End of ::
                        if (parsedDoubleColon ) {
                            // Only allowed one :: sequence
                            throw new IllegalArgumentException(
                                    sm.getString("http.tooManyDoubleColons"));
                        }
                        parsedDoubleColon = true;
                        // :: represents at least one h16 block
                        h16Count++;
                    }
                    precedingColonsCount++;
                    // mark if the next symbol is hex before the actual read
                    reader.mark(4);
                }
                h16Size = 0;
            } else if (c == ']') {
                if (precedingColonsCount == 1) {
                    // Can't end on a single ':'
                    throw new IllegalArgumentException(sm.getString("http.singleColonEnd"));
                }
                pos++;
                break;
            } else if (c == '.') {
                if (h16Count == 7 || h16Count < 7 && parsedDoubleColon) {
                    reader.reset();
                    pos -= h16Size;
                    pos += readHostIPv4(reader, true);
                    h16Count++;
                    break;
                } else {
                    throw new IllegalArgumentException(sm.getString("http.invalidIpv4Location"));
                }
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "http.illegalCharacterIpv6", Character.toString((char) c)));
            }
            pos++;
        } while (true);

        if (h16Count > 8) {
            throw new IllegalArgumentException(
                    sm.getString("http.tooManyHextets", Integer.toString(h16Count)));
        } else if (h16Count != 8 && !parsedDoubleColon) {
            throw new IllegalArgumentException(
                    sm.getString("http.tooFewHextets", Integer.toString(h16Count)));
        }

        c = reader.read();
        if (c == ':') {
            return pos;
        } else {
            if(c == -1) {
                return -1;
            }
            throw new IllegalArgumentException(
                    sm.getString("http.illegalAfterIpv6", Character.toString((char) c)));
        }
    }

    /**
     * @return The position of ':' that separates the host from the port or -1
     *         if it is not present
     */
    static int readHostDomainName(Reader reader) throws IOException {
        DomainParseState state = DomainParseState.NEW;
        int pos = 0;

        while (state.mayContinue()) {
            state = state.next(reader.read());
            pos++;
        }

        if (DomainParseState.COLON == state) {
            // State identifies the state of the previous character
            return pos - 1;
        } else {
            return -1;
        }
    }


    /**
     * Skips all characters until EOF or the specified target is found. Normally
     * used to skip invalid input until the next separator.
     */
    static SkipResult skipUntil(Reader input, int c, char target) throws IOException {
        while (c != -1 && c != target) {
            c = input.read();
        }
        if (c == -1) {
            return SkipResult.EOF;
        } else {
            return SkipResult.FOUND;
        }
    }


    private void relax(boolean[] flags, String relaxedChars) {
        if (relaxedChars != null && relaxedChars.length() > 0) {
            char[] chars = relaxedChars.toCharArray();
            for (char c : chars) {
                if (isRelaxable(c)) {
                    flags[c] = true;
                    IS_NOT_REQUEST_TARGET[c] = false;
                }
            }
        }
    }


    private enum DomainParseState {
        NEW(     true, false, false, false, " at the start of"),
        ALPHA(   true,  true,  true,  true, " after a letter in"),
        NUMERIC( true,  true,  true,  true, " after a number in"),
        PERIOD(  true, false, false,  true, " after a period in"),
        HYPHEN(  true,  true, false, false, " after a hypen in"),
        COLON(  false, false, false, false, " after a colon in"),
        END(    false, false, false, false, " at the end of");

        private final boolean mayContinue;
        private final boolean allowsHyphen;
        private final boolean allowsPeriod;
        private final boolean allowsEnd;
        private final String errorLocation;

        private DomainParseState(boolean mayContinue, boolean allowsHyphen, boolean allowsPeriod,
                boolean allowsEnd, String errorLocation) {
            this.mayContinue = mayContinue;
            this.allowsHyphen = allowsHyphen;
            this.allowsPeriod = allowsPeriod;
            this.allowsEnd = allowsEnd;
            this.errorLocation = errorLocation;
        }

        public boolean mayContinue() {
            return mayContinue;
        }

        public DomainParseState next(int c) {
            if (c == -1) {
                if (allowsEnd) {
                    return END;
                } else {
                    throw new IllegalArgumentException(
                            sm.getString("http.invalidSegmentEndState", this.name()));
                }
            } else if (HttpParser.isAlpha(c)) {
                return ALPHA;
            } else if (HttpParser.isNumeric(c)) {
                return NUMERIC;
            } else if (c == '.') {
                if (allowsPeriod) {
                    return PERIOD;
                } else {
                    throw new IllegalArgumentException(sm.getString("http.invalidCharacterDomain",
                            Character.toString((char) c), errorLocation));
                }
            } else if (c == ':') {
                if (allowsEnd) {
                    return COLON;
                } else {
                    throw new IllegalArgumentException(sm.getString("http.invalidCharacterDomain",
                            Character.toString((char) c), errorLocation));
                }
            } else if (c == '-') {
                if (allowsHyphen) {
                    return HYPHEN;
                } else {
                    throw new IllegalArgumentException(sm.getString("http.invalidCharacterDomain",
                            Character.toString((char) c), errorLocation));
                }
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "http.illegalCharacterDomain", Character.toString((char) c)));
            }
        }
    }
}
