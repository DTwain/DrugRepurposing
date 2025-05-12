package org.example.utils.events;

public class EntityChangeType<E> implements Event {
    private ChangeEventType type;
    private E data, oldData;

    public EntityChangeType(ChangeEventType type, E data) {
        this.type = type;
        this.data = data;
    }
    public EntityChangeType(ChangeEventType type, E data, E oldData) {
        this.type = type;
        this.data = data;
        this.oldData=oldData;
    }

    public ChangeEventType getType() {
        return type;
    }

    public E getData() {
        return data;
    }

    public E getOldData() {
        return oldData;
    }
}
