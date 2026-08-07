class Example {
    public static void main(String[] args) {
        int i;
        int x;
        int f;
        i = 50;
        x = 60;
        f = i + 9;
    }
}

class Orig {
    int x;
}

class Deriv extends Orig {
    int y;
}

class Deriv2 extends Deriv{
    int afejufiabn;
}

class Fin{
    public int sdf(Orig s){
        return 3;
    }
    public int sdfg(){
        int sdd;
        Orig x;
        x = new Deriv2();
        sdd = this.sdf(new Deriv2());
        return 345456;
    }
}