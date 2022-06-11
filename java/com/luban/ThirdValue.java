package com.luban;

public class ThirdValue  implements Value{
    protected Value next = null;
    @Override
    public Value getNext() {
        return next;
    }

    @Override
    public void setNext(Value value) {
        this.next = value;
    }

    @Override
    public void invoke(String handling) {
        handling = handling.replaceAll("zz","yy");
        System.out.println("Third 阀门处理完后：" + handling);
        getNext().invoke(handling);
    }
}
