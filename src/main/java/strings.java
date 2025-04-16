public class strings {

    public static void main(String[] args) {
        String animal1= "elefant";
        String animal2 = "rhyno";

        String first = animal1.substring(0,1);
        System.out.println(first);

        String last = animal2.substring(animal2.length()-1);
        System.out.println(last);

        int i = animal1.indexOf("a");
        System.out.println(i);


        String b = animal1.substring(0,i);

        String a = animal1.substring(i+1);

        System.out.println(b);
        System.out.println(a);
    }
}
