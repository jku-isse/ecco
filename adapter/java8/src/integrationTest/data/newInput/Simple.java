public class Simple {

    public Simple(String s) throws IllegalArgument {
        super(s);
    }

    public Simple() {
        super();
    }

    public <T, R extends Comparable<T> & T> void test(List<SpanShapeRenderer.Simple> uneedded, T... f) throws Exception {
        Supplier<String> a = (Serializable & Supplier<String>) () -> "TEST";
        Function<String, String> c = b -> b;
        synchronized (a) {
            for (int i = 0, y = 2; i < y; i += 2, y++) {
                new ArrayList<String>(1) {

                };
                try {
                    int i = super.y;
                } catch (OneException | OtherException e) {
                    throw e;
                }
                super.soManySpecialCases();
                String[] test1 = new String[]{};
                String[] test2 = new String[0];
            }
        }

    }
}