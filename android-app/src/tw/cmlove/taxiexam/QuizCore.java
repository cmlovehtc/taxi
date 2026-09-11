package tw.cmlove.taxiexam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Pure Java question parsing and selection; no Android or web UI dependencies. */
public final class QuizCore {
    public static final String[] CITIES = {"基隆市", "臺北市", "新北市", "桃園市", "宜蘭縣"};
    public static final class Question {
        public final String id, file, city, text;
        public final boolean tf;
        public final int number, answer, choices;
        public Question(String id, String file, String city, String text, boolean tf, int number, int answer, int choices) {
            this.id=id; this.file=file; this.city=city; this.text=text; this.tf=tf;
            this.number=number; this.answer=answer; this.choices=choices;
        }
        public String label() { return city + " · " + (tf ? "是非題" : "選擇題") + " " + number; }
        public String answerLabel() { return tf ? (answer==0 ? "是 ○" : "否 ×") : "選項 " + (answer+1); }
    }
    public static String sha256(byte[] bytes) {
        try { StringBuilder s=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) s.append(String.format(Locale.ROOT,"%02x", b & 255)); return s.toString(); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static List<Question> parse(String file, String source) {
        boolean tf=file.endsWith("是非題.txt");
        if(!tf && !file.endsWith("選擇題.txt")) throw new IllegalArgumentException("未知題型："+file);
        String city=file.startsWith("交通法令_") ? "交通法規" : file.split("_")[0];
        List<Question> list=new ArrayList<>(); Map<String,Integer> occurrences=new HashMap<>();
        Pattern ending=Pattern.compile("^(.*)答案\\s*[:：]\\s*([是否1-4])\\s*[。.]?\\s*$");
        Pattern option=Pattern.compile("[（(]([1-4])[）)]");
        for(String raw:source.replace("\ufeff", "").split("\\r?\\n")) {
            String line=raw.trim(); if(line.isEmpty()) continue;
            Matcher m=ending.matcher(line);
            if(!m.matches()) throw new IllegalArgumentException(file+" 第 "+(list.size()+1)+" 題答案格式不符");
            String body=m.group(1).trim(), a=m.group(2); int answer, choices;
            if(tf) { if(!a.equals("是") && !a.equals("否")) throw new IllegalArgumentException("是非題答案錯誤"); answer=a.equals("是")?0:1; choices=2; }
            else { answer=Integer.parseInt(a)-1; Matcher om=option.matcher(body); Set<Integer> found=new HashSet<>(); while(om.find()) found.add(Integer.parseInt(om.group(1))); choices=found.size(); if(choices<2 || choices>4 || !found.contains(answer+1)) throw new IllegalArgumentException(file+" 選項不完整"); for(int n=1;n<=choices;n++) if(!found.contains(n)) throw new IllegalArgumentException("選項編號不連續"); }
            String hash=sha256((file+"\n"+body+"\n"+a).getBytes(StandardCharsets.UTF_8));
            int occurrence=occurrences.containsKey(hash)?occurrences.get(hash):0; occurrences.put(hash,occurrence+1);
            list.add(new Question(hash+":"+occurrence,file,city,body,tf,list.size()+1,answer,choices));
        }
        if(list.isEmpty()) throw new IllegalArgumentException("空白題庫："+file);
        return list;
    }
    public static int[] quotas(int count,int major) {
        int[] q=new int[5]; int remaining=count; double[] fraction=new double[5];
        for(int i=0;i<5;i++) { double exact=count*(i==major?.6:.1); q[i]=(int)Math.floor(exact+1e-9); remaining-=q[i]; fraction[i]=exact-q[i]; }
        while(remaining-->0) { int best=major; for(int i=0;i<5;i++) if(fraction[i]>fraction[best]+1e-9) best=i; q[best]++; fraction[best]=-1; }
        return q;
    }
    public static List<Question> select(List<Question> all, String city, boolean law, boolean joint, int tfCount, int mcCount, Random random) {
        if(tfCount<0 || mcCount<0 || tfCount+mcCount==0 || tfCount+mcCount>500) throw new IllegalArgumentException("請設定 1～500 題；是非或選擇其中一種可以填 0。");
        List<Question> result=new ArrayList<>();
        for(int type=0;type<2;type++) {
            boolean tf=type==0; int count=tf?tfCount:mcCount;
            if(law || !joint) add(result,all,law?"交通法規":city,tf,count,random);
            else { int major=Arrays.asList(CITIES).indexOf(city); if(major<0) throw new IllegalArgumentException("此地區不適用五縣市配比"); int[] q=quotas(count,major); for(int i=0;i<5;i++) add(result,all,CITIES[i],tf,q[i],random); }
        }
        // Keep true/false and multiple choice in separate sections, matching the setup order.
        return result;
    }
    private static void add(List<Question> out,List<Question> all,String city,boolean tf,int count,Random random) {
        List<Question> candidates=new ArrayList<>(); for(Question q:all) if(q.city.equals(city)&&q.tf==tf) candidates.add(q);
        if(count>candidates.size()) throw new IllegalArgumentException(city+"的"+(tf?"是非":"選擇")+"題最多 "+candidates.size()+" 題，請減少題數。");
        Collections.shuffle(candidates,random); out.addAll(candidates.subList(0,count));
    }
    public static int nextUnanswered(int[] answers,int start) { for(int n=1;n<=answers.length;n++) {int i=(start+n)%answers.length;if(answers[i]<0)return i;} return -1; }
    public static int score(List<Question> questions,int[] answers) { int correct=0; for(int i=0;i<questions.size();i++)if(answers[i]==questions.get(i).answer)correct++;return Math.round(correct*100f/questions.size()); }
}
