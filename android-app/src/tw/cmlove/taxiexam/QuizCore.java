package tw.cmlove.taxiexam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Native port of the published website's question parser, sampling and exam rules. */
public final class QuizCore {
    public static final String[] CITIES = {"基隆市","臺北市","新北市","桃園市","宜蘭縣"};
    public static final class Question {
        public final String id,file,city,text,stem;
        public final boolean tf;
        public final int number,answer,choices;
        public final List<String> options;
        public Question(String id,String file,String city,String text,boolean tf,int number,int answer,int choices) {
            this.id=id;this.file=file;this.city=city;this.text=text;this.tf=tf;this.number=number;this.answer=answer;this.choices=choices;
            String visible=text.split("答案\\s*[:：]",2)[0].trim();List<String> values=new ArrayList<>();Map<Integer,String> optionMap=new TreeMap<>();String body=visible.replaceAll("\\((\\d+)\\)\\)+","($1)");String question=visible;
            if(!tf){Matcher m=Pattern.compile("\\((\\d+)\\)([^()]*)").matcher(body);boolean first=true;while(m.find()){if(first){question=body.substring(0,m.start()).trim();first=false;}optionMap.put(Integer.parseInt(m.group(1)),m.group(2).replaceAll("[。．]+$", "").trim());}}
            values.addAll(optionMap.values());stem=question;options=Collections.unmodifiableList(values);
        }
        public String webRegion(){return city.equals("交通法規")?"交通法令":city;}
        public String type(){return tf?"是非題":"選擇題";}
        public String webId(){return webRegion()+":"+type()+":"+number;}
        public String answerValue(){return tf?(answer==0?"是":"否"):String.valueOf(answer+1);}
        public String optionLabel(int n){return tf?(n==0?"○  是":"×  否"):"("+(n+1)+") "+(n<options.size()?options.get(n):"選項 "+(n+1));}
        public String label(){return city+" · "+type()+" "+number;}
        public String answerLabel(){return tf?answerValue():"("+(answer+1)+") "+(answer<options.size()?options.get(answer):"");}
        public String searchText(){return stem+" "+String.join(" ",options);}
    }
    public static String sha256(byte[] bytes) {
        try {char[] hex="0123456789abcdef".toCharArray();byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);char[] out=new char[64];for(int i=0;i<digest.length;i++){out[2*i]=hex[(digest[i]&255)>>>4];out[2*i+1]=hex[digest[i]&15];}return new String(out);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static List<Question> parse(String file,String source) {
        boolean tf=file.endsWith("是非題.txt");if(!tf&&!file.endsWith("選擇題.txt"))throw new IllegalArgumentException("未知題型："+file);
        String city=file.startsWith("交通法令_")?"交通法規":file.split("_")[0];
        List<Question> list=new ArrayList<>();Map<String,Integer> occurrences=new HashMap<>();
        Pattern ending=Pattern.compile("^(.*)答案\\s*[:：]\\s*([是否OXＯＸ○×1-9])\\s*[。.]?\\s*$",Pattern.CASE_INSENSITIVE);
        for(String raw:source.replace("\ufeff","").split("\\r?\\n")){
            String line=raw.trim();if(line.isEmpty())continue;Matcher m=ending.matcher(line);
            if(!m.matches())throw new IllegalArgumentException(file+" 第 "+(list.size()+1)+" 題答案格式不符");
            String body=m.group(1).trim(),a=m.group(2),identityAnswer=a;Matcher firstAnswer=Pattern.compile("答案\\s*[:：]\\s*([是否OXＯＸ○×1-9])",Pattern.CASE_INSENSITIVE).matcher(line);if(firstAnswer.find())a=firstAnswer.group(1);int answer,choices;
            if(tf){if("是OoＯ○".contains(a)){answer=0;a="是";}else if("否XxＸ×".contains(a)){answer=1;a="否";}else throw new IllegalArgumentException("是非題答案錯誤");choices=2;}
            else{answer=Integer.parseInt(a)-1;Matcher om=Pattern.compile("\\(([1-9])\\)").matcher(body);Set<Integer> found=new HashSet<>();while(om.find())found.add(Integer.parseInt(om.group(1)));choices=found.size();if(choices<2||choices>9||!found.contains(answer+1))throw new IllegalArgumentException(file+" 選項不完整");for(int n=1;n<=choices;n++)if(!found.contains(n))throw new IllegalArgumentException("選項編號不連續");}
            // Keep v0.1 content identities so existing progress survives installation updates.
            String hash=sha256((file+"\n"+body+"\n"+identityAnswer).getBytes(StandardCharsets.UTF_8));int occurrence=occurrences.getOrDefault(hash,0);occurrences.put(hash,occurrence+1);
            list.add(new Question(hash+":"+occurrence,file,city,body,tf,list.size()+1,answer,choices));
        }
        if(list.isEmpty())throw new IllegalArgumentException("空白題庫："+file);return list;
    }
    public static int available(List<Question> all,Map<String,Integer> ratios,boolean law,boolean tf){int n=0;for(Question q:all)if(q.tf==tf&&(law?q.city.equals("交通法規"):ratios.getOrDefault(q.city,0)>0))n++;return n;}
    public static List<Question> select(List<Question> all,Map<String,Integer> ratios,boolean law,int tfCount,int mcCount,Random random){
        if(tfCount<0||mcCount<0||(tfCount==0&&mcCount==0))throw new IllegalArgumentException("是非題與選擇題請填非負整數，且至少出一題。");
        if(!law){int sum=0;for(Map.Entry<String,Integer> e:ratios.entrySet()){if(!Arrays.asList(ExamProfiles.REGIONS).contains(e.getKey())||e.getValue()<0||e.getValue()>100)throw new IllegalArgumentException("縣市比例請填 0～100%。");sum+=e.getValue();}if(sum!=100)throw new IllegalArgumentException("縣市比例合計需為 100%，目前為 "+sum+"%。");}
        List<Question> result=new ArrayList<>();
        for(boolean tf:new boolean[]{true,false}){
            int count=tf?tfCount:mcCount,available=available(all,ratios,law,tf);if(count>available)throw new IllegalArgumentException((tf?"是非題":"選擇題")+"只有 "+available+" 題，請調整題數。");
            List<Question> selected=new ArrayList<>();Set<String> used=new HashSet<>();
            if(!law)for(String city:ExamProfiles.REGIONS){int percent=ratios.getOrDefault(city,0);if(percent<=0)continue;List<Question> pool=new ArrayList<>();for(Question q:all)if(q.city.equals(city)&&q.tf==tf)pool.add(q);Collections.shuffle(pool,random);int requested=(int)Math.floor(count*percent/100.0);for(Question q:pool.subList(0,Math.min(requested,pool.size()))){selected.add(q);used.add(q.id);}}
            // Website floors each region separately for each type, then fills from all unused active-region questions.
            List<Question> remainder=new ArrayList<>();for(String city:law?new String[]{"交通法規"}:ExamProfiles.REGIONS){if(!law&&ratios.getOrDefault(city,0)<=0)continue;for(Question q:all)if(q.city.equals(city)&&q.tf==tf&&!used.contains(q.id))remainder.add(q);}
            Collections.shuffle(remainder,random);selected.addAll(remainder.subList(0,count-selected.size()));if(!law)Collections.shuffle(selected,random);result.addAll(selected);
        }
        return result;
    }
    public static boolean isNewerBankVersion(String current,String candidate){try{return java.time.Instant.parse(candidate).isAfter(java.time.Instant.parse(current));}catch(Exception e){return false;}}
    public static int timeLimitSeconds(boolean mistakes,int tf,int mc){return !mistakes&&tf==20&&mc==30?3600:0;}
    public static int nextUnanswered(int[] answers,int start){return nextUnanswered(answers,start,1);}
    public static int nextUnanswered(int[] answers,int start,int direction){for(int n=1;n<answers.length;n++){int i=(start+direction*n+answers.length)%answers.length;if(answers[i]<0)return i;}return -1;}
    public static int score(List<Question> questions,int[] answers){int correct=0;for(int i=0;i<questions.size();i++)if(answers[i]==questions.get(i).answer)correct++;return questions.isEmpty()?0:Math.round(correct*100f/questions.size());}
}
