public class ArrayList<E> { //<E> is not saved

    private abstract void set(int i, E e);

    private class ListIter implements ListIterator {

        public <E> E set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.set(lastRet, e); // "ArrayList." is not saved
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
            return e;
        }
    }
}