import java.util.Scanner;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import java.io.File;

public class Main{

    BufferedReader reader;
    Pattern skip =  Pattern.compile("^#");
    Pattern begin =  Pattern.compile("^&");
    Pattern record = Pattern.compile("[0-9][.]+");
    double sum = 0.0D;
    int size = 0;
    int symbol = 0;

    public Main(String file){
        try{
            reader = new BufferedReader(new FileReader(new File(file)));
        }catch(Exception e){
            e.printStackTrace();
        }
        String str;
        try{
            while(true){
                str = reader.readLine();
                if(str==null || str.isEmpty()) break;
                if(skip.matcher(str).find()) continue;
                if(begin.matcher(str).find()){
                    if(size != 0){
                        System.out.println("AVG "+symbol+" : "+String.format("%.2f",(sum/size)));
                    }
                    sum = 0.0D;
                    size = 0;
                    symbol = Integer.parseInt(str.substring(1));
                }else if(record.matcher(str).find()){
                    sum += Double.parseDouble(str.substring(0, str.indexOf("user")));
                    size++;
                }
            }
            if(size != 0){
                System.out.println("AVG "+symbol+" : "+String.format("%.2f",(sum/size)));
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    public static void main(String[] args){
        new Main(args[0]);
    }
}