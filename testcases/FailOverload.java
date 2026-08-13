class Main {
    public static void main(String[] args) {
        System.out.println(new A().foo(1));
    }
}

class A {
    public int foo(int a) {
        return a;
    }
}

class B extends A {
    public int foo(boolean a) {
        return 1;
    }
}
