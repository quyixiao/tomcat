package com.luban;

import org.apache.catalina.core.NamingContextListener;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.deploy.ResourceBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.LookupRef;
import org.apache.naming.NamingContext;
import org.apache.naming.ResourceRef;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

public class MyNameContext {

    private static final Log log = LogFactory.getLog(NamingContextListener.class);

    public NamingContext getNamingContext() throws Exception {

        Hashtable<String, Object> contextEnv = new Hashtable<String, Object>();

        NamingContext namingContext = new NamingContext(contextEnv, "/");
        NamingContext envCtx = namingContext;

        ContextResource[] resources = new ContextResource[1];

        ContextResource resource = new ContextResource();

        resource.setAuth("Container");
        resource.setScope("Shareable");
        resource.setName("java:comp/env/jdbc/mysql");
        resource.setType("javax.sql.DataSource");

        resource.setProperty("driverClassName", "com.mysql.jdbc.Driver");
        resource.setProperty("url", "jdbc:mysql://172.16.157.238:3306/lz_test");
        resource.setProperty("username", "ldd_biz");
        resource.setProperty("password", "Hello1234");
        resource.setProperty("maxActive", "5");
        resource.setProperty("maxIdle", "2");
        resource.setProperty("maxWait", "10000");

        resources[0] = resource;

        for (int i = 0; i < resources.length; i++) {
            addResource(resources[i], envCtx);
        }
        return namingContext;
    }



    public static void addResource(ContextResource resource, NamingContext envCtx) {

        Reference ref = lookForLookupRef(resource);

        if (ref == null) {
            // Create a reference to the resource.
            ref = new ResourceRef(resource.getType(), resource.getDescription(),
                    resource.getScope(), resource.getAuth(), resource.getSingleton());
            // Adding the additional parameters, if any
            Iterator<String> params = resource.listProperties();
            while (params.hasNext()) {
                String paramName = params.next();
                String paramValue = (String) resource.getProperty(paramName);
                StringRefAddr refAddr = new StringRefAddr(paramName, paramValue);
                ref.add(refAddr);
            }
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("  Adding resource ref " + resource.getName() + "  " + ref);
            }
            createSubcontexts(envCtx, resource.getName());
            envCtx.bind(resource.getName(), ref);
        } catch (NamingException e) {
            log.error("异常", e);
        }

        if (("javax.sql.DataSource".equals(ref.getClassName()) ||
                "javax.sql.XADataSource".equals(ref.getClassName())) &&
                resource.getSingleton()) {
            try {
                ObjectName on = createObjectName(resource);
                Object actualResource = envCtx.lookup(resource.getName());
                System.out.println(actualResource);

                //    Registry.getRegistry(null, null).registerComponent(actualResource, on, null);
//                objectNames.put(resource.getName(), on);
            } catch (Exception e) {
                log.error("naming.jmxRegistrationFailed", e);
            }
        }

    }




    protected static ObjectName createObjectName(ContextResource resource)
            throws MalformedObjectNameException {

        String domain = null;
        if (domain == null) {
            domain = "Catalina";
        }

        ObjectName name = null;
        String quotedResourceName = ObjectName.quote(resource.getName());
        name = new ObjectName(domain + ":type=DataSource" +
                ",class=" + resource.getType() +
                ",name=" + quotedResourceName);

        return (name);

    }


    /**
     * Create all intermediate subcontexts.
     */
    private static void createSubcontexts(javax.naming.Context ctx, String name)
            throws NamingException {
        javax.naming.Context currentContext = ctx;
        StringTokenizer tokenizer = new StringTokenizer(name, "/");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if ((!token.equals("")) && (tokenizer.hasMoreTokens())) {
                try {
                    currentContext = currentContext.createSubcontext(token);
                } catch (NamingException e) {
                    // Silent catch. Probably an object is already bound in
                    // the context.
                    currentContext =
                            (javax.naming.Context) currentContext.lookup(token);
                }
            }
        }
    }


    private static LookupRef lookForLookupRef(ResourceBase resourceBase) {
        String lookupName = resourceBase.getLookupName();
        if ((lookupName != null && !lookupName.equals(""))) {
            return new LookupRef(resourceBase.getType(), lookupName);
        }
        return null;
    }



}
