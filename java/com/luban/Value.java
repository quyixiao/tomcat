package com.luban;

public interface Value {


    public Value getNext();

    public void setNext(Value value);

    public void invoke(String handling);


}
