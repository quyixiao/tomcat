package com.luban.transformlet.libs;

import java.io.*;


/**
 * IO utils
 */
public class IO {


    /**
     * Read file content to a String (always use utf-8)
     *
     * @param file The file to read
     * @return The String content
     */
    public static String readContentAsString(File file) {
        return readContentAsString(file, "utf-8");
    }

    /**
     * Read file content to a String
     *
     * @param file The file to read
     * @return The String content
     */
    public static String readContentAsString(File file, String encoding) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            StringWriter result = new StringWriter();
            PrintWriter out = new PrintWriter(result);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, encoding));
            String line = null;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
            return result.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    //
                }
            }
        }

        return null;
    }


    /**
     * Read binary content of a file (warning does not use on large file !)
     *
     * @param file The file te read
     * @return The binary data
     */
    public static byte[] readContent(File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            byte[] result = new byte[(int) file.length()];
            is.read(result);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    //
                }
            }
        }
        return null;
    }

    /**
     * Read binary content of a stream (warning does not use on large file !)
     *
     * @param is The stream to read
     * @return The binary data
     */
    public static byte[] readContent(InputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int read = 0;
            byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Write String content to a stream (always use utf-8)
     *
     * @param content The content to write
     * @param os      The stream to write
     */
    public static void writeContent(CharSequence content, OutputStream os) {
        writeContent(content, os, "utf-8");
    }

    /**
     * Write String content to a stream (always use utf-8)
     *
     * @param content The content to write
     * @param os      The stream to write
     */
    public static void writeContent(CharSequence content, OutputStream os, String encoding) {
        try {
            PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(os, encoding));
            printWriter.println(content);
            printWriter.flush();
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                os.close();
            } catch (Exception e) {
                //
            }
        }
    }

    /**
     * Write String content to a file (always use utf-8)
     *
     * @param content The content to write
     * @param file    The file to write
     */
    public static void writeContent(CharSequence content, File file) {
        writeContent(content, file, "utf-8");
    }

    /**
     * Write String content to a file (always use utf-8)
     *
     * @param content The content to write
     * @param file    The file to write
     */
    public static void writeContent(CharSequence content, File file, String encoding) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(os, encoding));
            printWriter.println(content);
            printWriter.flush();
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) os.close();
            } catch (Exception e) {
                //
            }
        }
    }

    /**
     * Write binay data to a file
     *
     * @param data The binary data to write
     * @param file The file to write
     */
    public static void write(byte[] data, File file) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data);
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) os.close();
            } catch (Exception e) {
                //
            }
        }
    }

    /**
     * Copy an stream to another one.
     */
    public static void copy(InputStream is, OutputStream os) {
        try {
            int read = 0;
            byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                //
            }
        }
    }

    /**
     * Copy an stream to another one.
     */
    public static void write(InputStream is, OutputStream os) {
        try {
            int read = 0;
            byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                //
            }
            try {
                os.close();
            } catch (Exception e) {
                //
            }
        }
    }

    /**
     * Copy an stream to another one.
     */
    public static void write(InputStream is, File f) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(f);
            int read = 0;
            byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                //
            }
            try {
                if (os != null) os.close();
            } catch (Exception e) {
                //
            }
        }
    }

    // If targetLocation does not exist, it will be created.
    public static void copyDirectory(File source, File target) {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdir();
            }
            for (String child : source.list()) {
                copyDirectory(new File(source, child), new File(target, child));
            }
        } else {
            try {
                write(new FileInputStream(source), new FileOutputStream(target));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
