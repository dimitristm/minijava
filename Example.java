class Example {
    public static void main(String[] args) {
        int[] i;
        int n;
        int f;
        // int x;
        // i = 10;
        boolean x;
        f = 10 + 10;
        // f = n + x;
        f = 1 * (n);
        // x = i + n;
    }
}
class Secondclass{
    int[] y;
    public int funfunc(){
        int y;
        y = 98;
        return 786;
    }
    public boolean funfun(){
        return true;
    }
}

class Third extends Secondclass{
    public int fun(){
        y[0] = 10;
        return 8;
    }
}

class Fourth extends Third{
    public int fun(){
        y[0] = 239;
        y = 787;
        return 45353;
    }
}