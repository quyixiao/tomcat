package com.luban;

import org.apache.naming.resources.WARDirContext;

import javax.naming.Name;
import java.util.zip.ZipEntry;

public class EscapedTest {

    public static void main(String[] args) {

        String name = "/aaaa/bbb/cccc/ddd";
        int pos = name.lastIndexOf('/');
        // Check that parent entries exist and, if not, create them.
        // This fixes a bug for war files that don't record separate
        // zip entries for the directories.
        int currentPos = -1;
        int lastPos = 0;

        while ((currentPos = name.indexOf('/', lastPos)) != -1) {
            String parentName = name.substring(0, lastPos);
            String childName = name.substring(0, currentPos);
            String entryName = name.substring(lastPos, currentPos);
            String zipName = "";
            if(currentPos > 1 ){
                zipName = name.substring(1, currentPos) + "/";
            }
            System.out.println("parentName=" + parentName + ",childName=" + childName + ",entryName="+entryName + ",zipName="+zipName+ ",lastPos="+lastPos + ",currentPos="+currentPos  );
            lastPos = currentPos + 1;
        }
    }
}
