package com.luban;

import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;

public class BeforeContextInitializedContainerListener  implements ContainerListener {

    @Override
    public void containerEvent(ContainerEvent event) {
        if("afterContextInitialized".equals(event.getType()) ||
                "beforeContextInitialized".equals(event.getType())){
            System.out.println("容器监听事件 "+ event.getType());
        }
    }
}
