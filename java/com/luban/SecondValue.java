package com.luban;

public class SecondValue  implements Value{
    protected Value next = null;
    @Override
    public Value getNext() {
        return next ;
    }

    @Override
    public void setNext(Value value) {
            this.next = value;
    }

    @Override
    public void invoke(String handling) {
        handling = handling.replaceAll("11","22");
        System.out.println("Second 阀门处理完：" + handling);
        getNext().invoke(handling);
    }
}
