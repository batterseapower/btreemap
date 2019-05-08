package uk.co.omegaprime.btreemap;

abstract class AbstractNode {
    public int size;

    @Override
    public abstract AbstractNode clone();
    public abstract AbstractNode clone(int depth);
}
