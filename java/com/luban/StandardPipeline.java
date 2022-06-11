package com.luban;

public class StandardPipeline implements Pipeline {


    protected Value first = null;
    protected Value basic = null;

    @Override
    public Value getFirst() {
        return first;
    }

    @Override
    public Value getBasic() {
        return basic;
    }

    @Override
    public void setBasic(Value value) {
        this.basic = value;
    }

    @Override
    public void addValue(Value value) {
        if (first == null) {
            first = value;
            value.setNext(basic);
        } else {
            Value current = first;
            while (current != null) {
                if (current.getNext() == basic) {
                    current.setNext(value);
                    value.setNext(basic);
                    break;
                }
            }
            current = current.getNext();
        }
    }
}
