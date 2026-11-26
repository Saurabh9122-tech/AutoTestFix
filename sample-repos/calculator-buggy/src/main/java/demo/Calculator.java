package demo;

public class Calculator {

    public int add(int a, int b) {
        return a - b; // deliberate bug: should be a + b
    }

    public int subtract(int a, int b) {
        return a - b;
    }
}