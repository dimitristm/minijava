class Main {
    public static void main(String[] a) {
        System.out.println(new B().runTest(10));
    }
}

class A extends Main {
    int i1;
    boolean b1;
    int[] iarr;

    public int init(int size) {
        i1 = size;
        b1 = true;
        iarr = new int[size];
        return 0;
    }

    public int getInt() {
        return i1;
    }
}

class B extends A {
    int i2;

    public int runTest(int size) {
        int dummy;
        int i3;
        dummy = this.init(size);
        i2 = 0;
        i3 = 0;

        while (i3 < (iarr.length)) {
            iarr[i3] = i3 * 2;
            i2 = i2 + (iarr[i3]);
            i3 = i3 + 1;
        }

        if (b1 && (0 < i1)) {
            System.out.println(i2);
        } else {
            System.out.println(0);
        }

        return this.getInt();
    }
}
