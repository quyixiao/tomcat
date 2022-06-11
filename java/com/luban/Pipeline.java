package com.luban;

public interface Pipeline {

    public Value getFirst();

    public Value getBasic();

    public void setBasic(Value value);


    public void addValue(Value value);
}
