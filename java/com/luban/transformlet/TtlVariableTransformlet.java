package com.luban.transformlet;


import com.luban.transformlet.libs.F.T2;
import com.luban.transformlet.libs.IO;
import javassist.*;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.LocalVariableAttribute;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * TTL {@link JavassistTransformlet} for {@link java.util.concurrent.Executor}.
 *
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @author wuwen5 (wuwen.55 at aliyun dot com)
 * @since 2.5.1
 */
public class TtlVariableTransformlet implements JavassistTransformlet {
    private static final Logger logger  = Logger.getLogger("TtlVariableTransformlet");


    public static String  getdir(String className){
        String temps [] = className.split("\\.");
        String dir = "";
        for(int i = 0 ;i < temps.length -1 ;i ++){
            dir = dir + "/" + temps[i];
        }
        return dir;
    }

    @Override
    public byte[] doTransform(String className, byte[] classFileBuffer, ClassLoader loader) throws IOException, NotFoundException, CannotCompileException {
        System.out.println("all class Name  :" + className);
        if (Utils.isNotNull(className) && className.equals("com.example.servelettest.Test")) {

            System.out.println("real doTransform class name :" + className);
            final CtClass ctClass = Utils.getCtClass(classFileBuffer, loader);

            String temp = className.replaceAll("\\.","/");
            File file = new File("/Users/quyixiao/github/Thread_NO_Known/origin" + getdir(className));
            if(!file.exists()){
                file.mkdirs();
            }

            file = new File("/Users/quyixiao/github/Thread_NO_Known/out" + getdir(className));
            if(!file.exists()){
                file.mkdirs();
            }
/*

           byte [] result = ctClass.toBytecode();
            IO.write(result, new File("/Users/quyixiao/github/Thread_NO_Known/origin/"+temp+".class"));

*/

            for (CtMethod method : ctClass.getDeclaredMethods()) {
                if (method.getName().contains("$")) {
                    // Generated method, skip
                    continue;
                }
                // Signatures names
                CodeAttribute codeAttribute = (CodeAttribute) method.getMethodInfo().getAttribute("Code");
                if (codeAttribute == null || javassist.Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                LocalVariableAttribute localVariableAttribute = (LocalVariableAttribute) codeAttribute.getAttribute("LocalVariableTable");
                List<T2<Integer, String>> parameterNames = new ArrayList<T2<Integer, String>>();
                if (localVariableAttribute == null) {
                    if (method.getParameterTypes().length > 0)
                        continue;
                } else {
                    if (localVariableAttribute.tableLength() < method.getParameterTypes().length + (javassist.Modifier.isStatic(method.getModifiers()) ? 0 : 1)) {
                        logger.info("weird: skipping method %s %s as its number of local variables is incorrect (lv=%s || lv.length=%s || params.length=%s || (isStatic? %s)"
                                + method.getReturnType().getName() + method.getLongName() + localVariableAttribute + (localVariableAttribute != null ? localVariableAttribute.tableLength() : -1)
                                + method.getParameterTypes().length + javassist.Modifier.isStatic(method.getModifiers()));
                    }
                    for (int i = 0; i < localVariableAttribute.tableLength(); i++) {

                        if (!localVariableAttribute.variableName(i).equals("__stackRecorder")) {
                            parameterNames.add(new T2<Integer, String>(localVariableAttribute.startPc(i) + localVariableAttribute.index(i), localVariableAttribute.variableName(i)));
                        }
                    }

                    Collections.sort(parameterNames, new Comparator<T2<Integer, String>>() {
                        public int compare(T2<Integer, String> o1, T2<Integer, String> o2) {
                            return o1._1.compareTo(o2._1);
                        }
                    });

                }
                List<String> names = new ArrayList<String>();
                for (int i = 0; i < method.getParameterTypes().length + (javassist.Modifier.isStatic(method.getModifiers()) ? 0 : 1); i++) {
                    if (localVariableAttribute == null) {
                        continue;
                    }
                    try {
                        String name = parameterNames.get(i)._2;
                        if (!name.equals("this")) {
                            names.add(name);
                        }
                    } catch (Exception e) {
                        System.out.println("exception 97");
                    }
                }
                StringBuilder iv = new StringBuilder();
                if (names.isEmpty()) {
                    iv.append("new String[0];");
                } else {
                    iv.append("new String[] {");
                    for (Iterator<String> i = names.iterator(); i.hasNext(); ) {
                        iv.append("\"");
                        String aliasedName = i.next();
                        if (aliasedName.contains("$")) {
                            aliasedName = aliasedName.substring(0, aliasedName.indexOf("$"));
                        }
                        iv.append(aliasedName);
                        iv.append("\"");
                        if (i.hasNext()) {
                            iv.append(",");
                        }
                    }
                    iv.append("};");
                }

                String sigField = "$" + method.getName() + LocalvariablesNamesEnhancer.LocalVariablesNamesTracer.computeMethodHash(method.getParameterTypes());
                try { // #1198
                    ctClass.getDeclaredField(sigField);
                } catch (NotFoundException nfe) {
                    CtField signature = CtField.make("public static String[] " + sigField + " = " + iv.toString(), ctClass);
                    ctClass.addField(signature);
                }

                if (localVariableAttribute == null) {
                    continue;
                }

                // OK.
                // Here after each local variable creation instruction,
                // we insert a call to com.linzi.utils.LocalVariables.addVariable('var', var)
                // without breaking everything...
                for (int i = 0; i < localVariableAttribute.tableLength(); i++) {

                    // name of the local variable
                    String name = localVariableAttribute.getConstPool().getUtf8Info(localVariableAttribute.nameIndex(i));
                    System.out.println("变量名="+name);

                    // Normalize the variable name
                    // For several reasons, both variables name and name$1 will be aliased to name
                    String aliasedName = name;
                    if (aliasedName.contains("$")) {
                        aliasedName = aliasedName.substring(0, aliasedName.indexOf("$"));
                    }


                    if (name.equals("this")) {
                        continue;
                    }

                /* DEBUG
                IO.write(ctClass.toBytecode(), new File("/tmp/lv_"+applicationClass.name+".class"));
                ctClass.defrost();
                 */

                    try {

                        // The instruction at which this local variable has been created
                        Integer pc = localVariableAttribute.startPc(i);

                        // Move to the next instruction (insertionPc)
                        CodeIterator codeIterator = codeAttribute.iterator();
                        codeIterator.move(pc);
                        pc = codeIterator.next();

                        Bytecode b = makeBytecodeForLVStore(method, localVariableAttribute.signature(i), name, localVariableAttribute.index(i));
                        codeIterator.insert(pc, b.get());
                        codeAttribute.setMaxStack(codeAttribute.computeMaxStack());

                        // Bon chaque instruction de cette méthode
                        while (codeIterator.hasNext()) {
                            int index = codeIterator.next();
                            int op = codeIterator.byteAt(index);

                            String option = Factory.NewInstruction(op);
                            System.out.println("op =" + op + ",option="+option);
                            // DEBUG
                            // printOp(op);

                            int varNumber = -1;
                            // The variable changes
                            if (storeByCode.containsKey(op)) {
                                varNumber = storeByCode.get(op);
                                if (varNumber == -2) {
                                    varNumber = codeIterator.byteAt(index + 1);
                                }
                            }

                            // Si c'est un store de la variable en cours d'examination
                            // et que c'est dans la frame d'utilisation de cette variable on trace l'affectation.
                            // (en fait la frame commence à localVariableAttribute.startPc(i)-1 qui est la première affectation
                            //  mais aussi l'initialisation de la variable qui est deja tracé plus haut, donc on commence à localVariableAttribute.startPc(i))
                            if (varNumber == localVariableAttribute.index(i) && index < localVariableAttribute.startPc(i) + localVariableAttribute.codeLength(i)) {
                                b = makeBytecodeForLVStore(method, localVariableAttribute.signature(i), aliasedName, varNumber);
                                codeIterator.insertEx(b.get());
                                codeAttribute.setMaxStack(codeAttribute.computeMaxStack());
                            }
                        }
                    } catch (Exception e) {
                        // Well probably a compiled optimizer (I hope so)
                    }

                }

                // init variable tracer
                method.insertBefore("com.luban.transformlet.LocalvariablesNamesEnhancer.LocalVariablesNamesTracer.enter();");
                method.insertAfter("com.luban.transformlet.LocalvariablesNamesEnhancer.LocalVariablesNamesTracer.exit();", true);

            }
            // Done.
            byte [] result = ctClass.toBytecode();
            IO.write(result, new File("/Users/quyixiao/github/servelet-test/out/"+temp+".class"));
            ctClass.defrost();

            return result;
        }
        return classFileBuffer;
    }


    private static Bytecode makeBytecodeForLVStore(CtMethod method, String sig, String name, int slot) {
        Bytecode b = new Bytecode(method.getMethodInfo().getConstPool());
        b.addLdc(name);
        if ("I".equals(sig) || "B".equals(sig) || "C".equals(sig) || "S".equals(sig) || "Z".equals(sig))
            b.addIload(slot);
        else if ("F".equals(sig))
            b.addFload(slot);
        else if ("J".equals(sig))
            b.addLload(slot);
        else if ("D".equals(sig))
            b.addDload(slot);
        else
            b.addAload(slot);

        String localVarDescriptor = sig;
        if (!"B".equals(sig) && !"C".equals(sig) && !"D".equals(sig) && !"F".equals(sig) &&
                !"I".equals(sig) && !"J".equals(sig) && !"S".equals(sig) && !"Z".equals(sig))
            localVarDescriptor = "Ljava/lang/Object;";

        b.addInvokestatic("com.luban.transformlet.LocalvariablesNamesEnhancer$LocalVariablesNamesTracer", "addVariable", "(Ljava/lang/String;" + localVarDescriptor + ")V");
        return b;
    }


    private final static Map<Integer, Integer> storeByCode = new HashMap<Integer, Integer>();

    /**
     * Useful instructions
     */
    static {
        storeByCode.put(CodeIterator.ASTORE_0, 0);
        storeByCode.put(CodeIterator.ASTORE_1, 1);
        storeByCode.put(CodeIterator.ASTORE_2, 2);
        storeByCode.put(CodeIterator.ASTORE_3, 3);
        storeByCode.put(CodeIterator.ASTORE, -2);

        storeByCode.put(CodeIterator.ISTORE_0, 0);
        storeByCode.put(CodeIterator.ISTORE_1, 1);
        storeByCode.put(CodeIterator.ISTORE_2, 2);
        storeByCode.put(CodeIterator.ISTORE_3, 3);
        storeByCode.put(CodeIterator.ISTORE, -2);
        storeByCode.put(CodeIterator.IINC, -2);

        storeByCode.put(CodeIterator.LSTORE_0, 0);
        storeByCode.put(CodeIterator.LSTORE_1, 1);
        storeByCode.put(CodeIterator.LSTORE_2, 2);
        storeByCode.put(CodeIterator.LSTORE_3, 3);
        storeByCode.put(CodeIterator.LSTORE, -2);

        storeByCode.put(CodeIterator.FSTORE_0, 0);
        storeByCode.put(CodeIterator.FSTORE_1, 1);
        storeByCode.put(CodeIterator.FSTORE_2, 2);
        storeByCode.put(CodeIterator.FSTORE_3, 3);
        storeByCode.put(CodeIterator.FSTORE, -2);

        storeByCode.put(CodeIterator.DSTORE_0, 0);
        storeByCode.put(CodeIterator.DSTORE_1, 1);
        storeByCode.put(CodeIterator.DSTORE_2, 2);
        storeByCode.put(CodeIterator.DSTORE_3, 3);
        storeByCode.put(CodeIterator.DSTORE, -2);
    }


}
