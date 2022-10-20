/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.luban;

import org.apache.catalina.deploy.WebXml;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test case for {@link WebXml} fragment ordering.
 */
public class TestWebXmlOrdering {
    private WebXml app;
    private WebXml a;
    private WebXml b;
    private WebXml c;
    private WebXml d;
    private WebXml e;
    private WebXml f;
    private Map<String, WebXml> fragments;
    private int posA;
    private int posB;
    private int posC;
    private int posD;
    private int posE;
    private int posF;

    @Before
    public void setUp() {
        app = new WebXml();
        a = new WebXml();
        a.setName("a");
        b = new WebXml();
        b.setName("b");
        c = new WebXml();
        c.setName("c");
        d = new WebXml();
        d.setName("d");
        e = new WebXml();
        e.setName("e");
        f = new WebXml();
        f.setName("f");
        // Control the input order
        fragments = new LinkedHashMap<String, WebXml>();
        fragments.put("a", a);
        fragments.put("b", b);
        fragments.put("c", c);
        fragments.put("d", d);
        fragments.put("e", e);
        fragments.put("f", f);
    }

    @Test
    public void testOrderWebFragmentsAbsolute() {
        app.addAbsoluteOrdering("c");
        app.addAbsoluteOrdering("a");
        app.addAbsoluteOrdering("b");

        print();
    }

    public void print() {
        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);
        Iterator<WebXml> iter = ordered.iterator();
        while (iter.hasNext()) {
            WebXml webXml = iter.next();
            System.out.println(webXml.getName());
        }
    }

    @Test
    public void testOrderWebFragmentsAbsolutePartial() {
        app.addAbsoluteOrdering("c");
        app.addAbsoluteOrdering("a");

        print();
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersStart() {
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("d");

        print();
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersMiddle() {
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("d");
        print();
    }

    @Test
    public void testWebFragmentsAbsoluteWrongFragmentName() {
        app.addAbsoluteOrdering("a");
        app.addAbsoluteOrdering("z");
        print();
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersEnd() {
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("d");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        print();
    }


    @Test
    public void testOrderWebFragmentsRelative1() {
        // First example from servlet spec

        a.addAfterOrderingOthers();
        a.addAfterOrdering("c");
        b.addBeforeOrderingOthers();
        c.addAfterOrderingOthers();
        f.addBeforeOrderingOthers();
        f.addBeforeOrdering("b");

        print();
    }

    @Test
    public void testOrderWebFragmentsRelative2() {
        a.addAfterOrderingOthers();
        a.addBeforeOrdering("c");
        b.addBeforeOrderingOthers();
        d.addAfterOrderingOthers();
        e.addBeforeOrderingOthers();
        print();

    }

    @Test
    public void testOrderWebFragmentsRelative3() {
        // Third example from spec with e & f added
        a.addAfterOrdering("b");
        c.addBeforeOrderingOthers();
        print();
    }

    @Test
    public void testOrderWebFragmentsRelative4Bug54068() {
        b.addAfterOrdering("a");
        c.addAfterOrdering("b");
        print();

    }

    @Test
    public void testOrderWebFragmentsRelative5Bug54068() {
        // Simple sequence that failed for some inputs
        b.addBeforeOrdering("a");
        c.addBeforeOrdering("b");
        print();

    }

    @Test
    public void testOrderWebFragmentsRelative6Bug54068() {
        b.addBeforeOrdering("a");
        b.addAfterOrdering("c");
        print();
    }

    @Test
    public void testOrderWebFragmentsRelative7() {
        // Reference loop (but not circular dependencies)
        b.addBeforeOrdering("a");
        c.addBeforeOrdering("b");
        a.addAfterOrdering("c");
        print();
    }

    @Test
    public void testOrderWebFragmentsRelative8() {
        // More complex, trying to break the algorithm

        a.addBeforeOrderingOthers();
        a.addBeforeOrdering("b");
        b.addBeforeOrderingOthers();
        c.addAfterOrdering("b");
        d.addAfterOrdering("c");
        e.addAfterOrderingOthers();
        f.addAfterOrderingOthers();
        f.addAfterOrdering("e");
        print();

    }

    @Test
    public void testOrderWebFragmentsRelative9() {
        // Variation on bug 54068
        a.addBeforeOrderingOthers();
        b.addBeforeOrdering("a");
        c.addBeforeOrdering("b");
        print();
    }

    @Test
    public void testOrderWebFragmentsRelative10() {
        // Variation on bug 54068
        a.addAfterOrderingOthers();
        b.addAfterOrdering("a");
        c.addAfterOrdering("b");
        print();
    }

    @Test
    public void testOrderWebFragmentsRelative11() {
        // Test references to non-existant fragments
        a.addAfterOrdering("b");
        b.addAfterOrdering("z");
        b.addBeforeOrdering("y");
        print();
    }

    @Test
    public void testOrderWebFragmentsrelativeCircular1() {
        a.addBeforeOrdering("b");
        b.addBeforeOrdering("a");

        print();
    }

    public void testOrderWebFragmentsrelativeCircular2() {
        a.addBeforeOrderingOthers();
        b.addAfterOrderingOthers();
        c.addBeforeOrdering("a");
        c.addAfterOrdering("b");
        print();
    }


}
