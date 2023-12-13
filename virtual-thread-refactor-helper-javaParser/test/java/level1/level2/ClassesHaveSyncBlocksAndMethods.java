package level1.level2;

class ClassHasSyncBlocksAndMethods {

    public void syncBlock() {
        synchronized(this){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

    public synchronized void syncMethod() {
        System.out.println("hello");
    }

}

class NonStaticComponent {

    public void syncBlock() {
        synchronized(this){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

    public synchronized void syncMethod() {
        System.out.println("hello");
    }
}

class ClassHasSyncBlockUsingNonStaticComponent {

    private NonStaticComponent component;
    public void syncBlock() {
        synchronized(component){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

}


class StaticComponent {

}

class ClassHasSyncBlockUsingStaticComponent {

    private static StaticComponent component;
    public void syncBlock() {
        synchronized(component){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

}