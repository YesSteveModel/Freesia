package meow.kikir.freesia.common.utils;

public class LinkedObjects<T> {
    private int currentId;
    private T value;

    private LinkedObjects<T> next;

    public void setValue(int id, T value) {
        if (id < this.currentId) {
            throw new IllegalArgumentException("Given Id is smaller than current linked object Id");
        }

        if (id == this.currentId) {
            this.value = value;
            return;
        }

        this.ensureNextNodeCreated();
        this.next.setValue(id, value);
    }

    public void upgradeToNext() {
        // the tail of the link reached, abort
        if (this.next == null) {
            return;
        }

        this.value = this.next.value;
        this.currentId = this.next.currentId;
        this.next = this.next.next; // forward by 1 id
    }

    public T getValue() {
        return this.value;
    }

    public int getCurrentId() {
        return this.currentId;
    }

    public T next() {
        // we are at the tail of the link
        // return null directly
        if (this.next == null) {
            return null;
        }

        return this.next.getValue();
    }

    public T getById(int id) {
        if (id < this.currentId) {
            return null;
        }

        if (id == this.currentId) {
            return this.value;
        }

        if (this.next == null) {
            return null;
        }

        return this.next.getById(id);
    }

    private void ensureNextNodeCreated() {
        if (this.next != null) {
            return;
        }

        this.next = new LinkedObjects<>();
        this.next.currentId = this.currentId + 1;
    }
}
