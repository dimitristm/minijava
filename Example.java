class Main {
    public static void main(String[] a) {
        System.out.println(new B().runTest(10));
    }
}

class A extends Main {
    int myInt;
    boolean myBool;
    int[] myArray;

    public int init(int size) {
        myInt = size;
        myBool = true;
        myArray = new int[size];
        return 0;
    }

    public int getInt() {
        return myInt;
    }
}

class B extends A {
    int anotherInt;

    public int runTest(int size) {
        int dummy;
        int i;
        dummy = this.init(size);
        anotherInt = 0;
        i = 0;

        while (i < (myArray.length)) {
            myArray[i] = i * 2;
            anotherInt = anotherInt + (myArray[i]);
            i = i + 1;
        }

        if (myBool && (0 < myInt)) {
            System.out.println(anotherInt);
        } else {
            System.out.println(0);
        }

        return this.getInt();
    }
}