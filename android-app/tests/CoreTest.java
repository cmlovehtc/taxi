import tw.cmlove.taxiexam.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class CoreTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static void rejects(Runnable run){try{run.run();throw new AssertionError("Invalid request accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        List<QuizCore.Question> all=new ArrayList<>();try(java.util.stream.Stream<Path> paths=Files.list(Paths.get(args[0]))){paths.filter(p->p.toString().endsWith(".txt")).sorted().forEach(p->{try{all.addAll(QuizCore.parse(p.getFileName().toString(),new String(Files.readAllBytes(p),StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}});}
        check(all.size()==6610,"The full real bank must be present");Set<String> ids=new HashSet<>();for(QuizCore.Question q:all){check(ids.add(q.id),"Unique IDs");if(!q.tf)check(q.options.size()==q.choices&&!q.stem.contains("(1)"),"Options separate from stem: "+q.webId()+" "+q.text+" size="+q.options.size()+" choices="+q.choices);}
        for(String city:ExamProfiles.REGISTRATION_REGIONS){Map<String,Integer> ratios=ExamProfiles.ratios(city);check(ratios.values().stream().mapToInt(Integer::intValue).sum()==100,"Profile sums to 100");List<QuizCore.Question> selected=QuizCore.select(all,ratios,false,20,30,new Random(19));check(selected.size()==50,"50 questions");Set<String> seen=new HashSet<>();for(int i=0;i<50;i++){QuizCore.Question q=selected.get(i);check(q.tf==(i<20),"TF20 then MC30");check(ratios.getOrDefault(q.city,0)>0&&seen.add(q.id),"Only active cities, no repeats");}
            for(boolean tf:new boolean[]{true,false}){int count=tf?20:30;int remainder=count-ratios.values().stream().mapToInt(p->count*p/100).sum();for(Map.Entry<String,Integer> r:ratios.entrySet()){long actual=selected.stream().filter(q->q.tf==tf&&q.city.equals(r.getKey())).count();int floor=count*r.getValue()/100;check(actual>=floor&&actual<=floor+remainder,"Per-type floor then random fill: "+city+"/"+r.getKey());}}
        }
        check(ExamProfiles.ratios("臺北市").get("臺北市")==30,"Taipei is 30%, not 60%");check(ExamProfiles.ratios("桃園市").containsKey("新竹縣")&&!ExamProfiles.ratios("桃園市").containsKey("宜蘭縣"),"Taoyuan includes Hsinchu");check(ExamProfiles.ratios("宜蘭縣").get("花蓮縣")==10,"Yilan includes Hualien");
        Map<String,Integer> one=Collections.singletonMap("基隆市",100),custom=new LinkedHashMap<>();custom.put("基隆市",40);custom.put("臺北市",60);check(QuizCore.select(all,custom,false,20,30,new Random()).stream().filter(q->q.city.equals("基隆市")).count()==20,"Custom percentages honored");
        rejects(()->QuizCore.select(all,Collections.singletonMap("基隆市",99),false,20,30,new Random()));rejects(()->QuizCore.select(all,one,false,0,0,new Random()));rejects(()->QuizCore.select(all,one,false,9999,0,new Random()));
        check(QuizCore.select(all,one,false,0,5,new Random()).size()==5,"One type can be zero");check(QuizCore.select(all,Collections.emptyMap(),true,20,30,new Random()).size()==50,"Law needs no city");
        Map<String,Integer> skewed=new LinkedHashMap<>();skewed.put("連江縣",99);skewed.put("臺北市",1);List<QuizCore.Question> fallback=QuizCore.select(all,skewed,false,150,0,new Random());check(fallback.size()==150,"A depleted region falls back to other active regions, matching web");
        check(QuizCore.timeLimitSeconds(false,20,30)==3600,"Official 60 minute timer");check(QuizCore.timeLimitSeconds(false,10,40)==0&&QuizCore.timeLimitSeconds(true,20,30)==0,"Custom/mistakes untimed");
        check(QuizCore.nextUnanswered(new int[]{0,-1,2,-1},3)==1,"Wrap skips answered");check(QuizCore.nextUnanswered(new int[]{0,-1,2,-1},1,-1)==3,"Previous wraps skipping answered");check(QuizCore.nextUnanswered(new int[]{0,1,-1},2)==-1,"Exclude current question");check(QuizCore.nextUnanswered(new int[]{0,1,2},2)==-1,"All answered");
        String before="第一題。答案:是\n第二題。答案:否\n";check(QuizCore.parse("基隆市_地理環境_是非題.txt",before).get(0).id.equals(QuizCore.parse("基隆市_地理環境_是非題.txt","新增題。答案:否\n"+before).get(1).id),"Stable progress when questions inserted");
        List<QuizCore.Question> paper=all.subList(0,50);int[] answers=new int[50];Arrays.fill(answers,-1);for(int i=0;i<35;i++)answers[i]=paper.get(i).answer;check(QuizCore.score(paper,answers)==70,"35/50 = pass 70");
        String date="2026-09-02T18:40:20Z";check(!QuizCore.isNewerBankVersion(date,date),"Repeated official checks do not change bank content");check(QuizCore.isNewerBankVersion(date,"2026-09-11T18:40:20Z"),"Changed content version updates");check(!QuizCore.isNewerBankVersion(date,"2026-08-01T00:00:00Z"),"Never downgrade bank");check(!QuizCore.isNewerBankVersion(date,"invalid"),"Ignore invalid content date");
        System.out.println("PASS: 6610 questions, 22 canonical profiles, 20/30 order, 60 minute rule, custom ratios, depleted-region fallback, stable IDs and navigation.");
    }
}
