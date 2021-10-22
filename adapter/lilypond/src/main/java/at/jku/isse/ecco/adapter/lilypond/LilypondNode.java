package at.jku.isse.ecco.adapter.lilypond;

public class LilypondNode<T> {
    private final String name;
    private final T data;
    private int level = -1;
    private LilypondNode<T> prev;
    private LilypondNode<T> next;

    public String getName() {
        return name;
    }

    public T getData() {
        return data;
    }

    public int getLevel() { return level; }

    public LilypondNode<T> getPrev() {
        return prev;
    }

    public LilypondNode<T> getNext() { return next; }

    public LilypondNode(String name, T data) {
        this.name = name;
        this.data = data;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void changeLevelBy(int offset) {
        level += offset;
    }

    public void setPrev(LilypondNode<T> prev) {
        this.prev = prev;
    }

    public void setNext(LilypondNode<T> next) {
        this.next = next;
    }

    public void append(LilypondNode<T> node, int level) {
        next = node;
        next.setPrev(this);
        next.setLevel(level);
    }

    public LilypondNode<T> append(String name, T data, int level) {
        LilypondNode<T> n = new LilypondNode(name, data);
        append(n, level);
        return n;
    }

    public void remove() {
        if (null != prev) {
            prev.setNext(null);
            prev = null;
        }
    }

    public void cut() {
        if (null != prev) {
            prev.setNext(next);
            if (null != next) {
                next.setPrev(prev);
            }
        } else if (null != next) {
            next.setPrev(null);
        }
        prev = null;
        next = null;
    }

    public void insertAfter(LilypondNode<T> node) {
        next = node.getNext();
        if (next != null) {
            next.setPrev(this);
        }
        node.setNext(this);
        prev = node;
    }
}
