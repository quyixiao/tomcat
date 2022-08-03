package com.luban;

import javax.crypto.interfaces.PBEKey;
import javax.naming.Context;
import java.util.Hashtable;

public class RemoveTest {

    public static void main(String[] args) {
        Hashtable<Object, Object> threadBindings = new Hashtable<Object,Object>();

        threadBindings.remove(1l);

    }
}
