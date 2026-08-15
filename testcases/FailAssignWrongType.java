class Main {
    public static void main(String[] a) {
        System.out.println(new Test1().run());
    }
}

class Test1 {
    public int run() {
        boolean b;
        int i;
        b = true;
        i = b;
        return i;
    }
}
