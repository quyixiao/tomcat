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
package org.apache.catalina.util;

import java.util.Locale;

/**
 * Utility class to manage context names so there is one place where the
 * conversions between baseName, path and version take place.
 *      在 Web 应用加载的最后，我们讲解一下Context命名规则，尽管在大多数情况下，Context 的名称与部署目录名称或者WAR包名称（去除扩展名，下文名）
 * 为基础文件名称相同，但是，Tomcat 支持的命名规则要复杂得多，在部署简单的情况下，我们基本可以忽略Tomcat 对Context命名规则的处理，
 * 但是在复杂的逻辑情况下，这可能给我们应用部署带来极大的便利性。
 *      实际上，Context的name,path和version的3个属性和基础文件名称有非常紧密的关系 。
 *      当未指定version时，name与path相同，如果path为空字符串，基础文件名为"ROOT",否则，将path起始的"/"删除，并将其余的"/"替换成
 * # 即为基础文件名称 。
 *
 *
 */
public final class ContextName {
    private static final String ROOT_NAME = "ROOT";
    private static final String VERSION_MARKER = "##";
    private static final String FWD_SLASH_REPLACEMENT = "#";

    private final String baseName;
    private final String path;
    private final String version;
    private final String name;

    /**
     * Creates an instance from a context name, display name, base name,
     * directory name, WAR name or context.xml name.
     *
     * @param name  The name to use as the basis for this object
     *
     * @deprecated  Use {@link ContextName#ContextName(String, boolean)}
     */
    @Deprecated
    public ContextName(String name) {
        this(name, true);
    }


    /**
     * Creates an instance from a context name, display name, base name,
     * directory name, WAR name or context.xml name.
     *
     * @param name  The name to use as the basis for this object
     * @param stripFileExtension    If a .war or .xml file extension is present
     *                              at the end of the provided name should it be
     *                              removed?
     */
    public ContextName(String name, boolean stripFileExtension) {

        String tmp1 = name;

        // Convert Context names and display names to base names

        // Strip off any leading "/"
        if (tmp1.startsWith("/")) {
            tmp1 = tmp1.substring(1);
        }

        // Replace any remaining /
        tmp1 = tmp1.replaceAll("/", FWD_SLASH_REPLACEMENT);

        // Insert the ROOT name if required
        if (tmp1.startsWith(VERSION_MARKER) || "".equals(tmp1)) {
            tmp1 = ROOT_NAME + tmp1;
        }

        // Remove any file extensions
        if (stripFileExtension &&
                (tmp1.toLowerCase(Locale.ENGLISH).endsWith(".war") ||
                        tmp1.toLowerCase(Locale.ENGLISH).endsWith(".xml"))) {
            tmp1 = tmp1.substring(0, tmp1.length() -4);
        }

        baseName = tmp1;

        String tmp2;
        // Extract version number
        int versionIndex = baseName.indexOf(VERSION_MARKER);
        if (versionIndex > -1) {
            version = baseName.substring(versionIndex + 2);
            tmp2 = baseName.substring(0, versionIndex);
        } else {
            version = "";
            tmp2 = baseName;
        }

        // 应用名字为ROOT，那么path为""
        if (ROOT_NAME.equals(tmp2)) {
            path = "";
        } else {
            path = "/" + tmp2.replaceAll(FWD_SLASH_REPLACEMENT, "/");
        }

        if (versionIndex > -1) {
            this.name = path + VERSION_MARKER + version;
        } else {
            this.name = path;
        }
    }

    /**
     * Construct an instance from a path and version.
     *
     * @param path      Context path to use
     * @param version   Context version to use
     */
    public ContextName(String path, String version) {
        // Path should never be null, '/' or '/ROOT'
        if (path == null || "/".equals(path) || "/ROOT".equals(path)) {
            this.path = "";
        } else {
            this.path = path;
        }

        // Version should never be null
        if (version == null) {
            this.version = "";
        } else {
            this.version = version;
        }

        // Name is path + version
        if ("".equals(this.version)) {
            name = this.path;
        } else {
            name = this.path + VERSION_MARKER + this.version;
        }

        // Base name is converted path + version
        StringBuilder tmp = new StringBuilder();
        if ("".equals(this.path)) {
            tmp.append(ROOT_NAME);
        } else {
            tmp.append(this.path.substring(1).replaceAll("/",
                    FWD_SLASH_REPLACEMENT));
        }
        if (this.version.length() > 0) {
            tmp.append(VERSION_MARKER);
            tmp.append(this.version);
        }
        this.baseName = tmp.toString();
    }

    public String getBaseName() {
        return baseName;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        StringBuilder tmp = new StringBuilder();
        if ("".equals(path)) {
            tmp.append('/');
        } else {
            tmp.append(path);
        }

        if (!"".equals(version)) {
            tmp.append(VERSION_MARKER);
            tmp.append(version);
        }

        return tmp.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
