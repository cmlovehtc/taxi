import tw.cmlove.taxiexam.QuizCore;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CoreTest {
    static void check(boolean yes,String message) {if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        List<QuizCore.Question> all=new ArrayList<>();
        try(java.util.stream.Stream<Path> paths=Files.list(Paths.get(args[0]))) {paths.filter(p->p.toString().endsWith(".txt")).sorted().forEach(p->{try{all.addAll(QuizCore.parse(p.getFileName().toString(),new String(Files.readAllBytes(p),StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}});}
        check(all.size()>500,"Real bundled question bank must be present");
        Set<String> ids=new HashSet<>();for(QuizCore.Question q:all)check(ids.add(q.id),"Question IDs must be unique");
        for(int n=0;n<=500;n++)for(int major=0;major<5;major++){int[] quotas=QuizCore.quotas(n,major);check(Arrays.stream(quotas).sum()==n,"Weights must preserve exact requested count");check(quotas[major]>=Arrays.stream(quotas).max().getAsInt(),"Major city must get largest share");}
        boolean five=true;for(String city:QuizCore.CITIES){boolean found=false;for(QuizCore.Question q:all)if(q.city.equals(city))found=true;five&=found;}
        if(five) {
            for(String city:QuizCore.CITIES){List<QuizCore.Question> test=QuizCore.select(all,city,false,true,10,40,new Random(1));check(test.size()==50,"50 questions");long main=test.stream().filter(q->q.city.equals(city)).count();check(main==30,"Main region must be 60%");Set<String> selected=new HashSet<>();for(QuizCore.Question q:test)check(selected.add(q.id),"No repeated questions");}
            check(QuizCore.select(all,"基隆市",false,false,0,5,new Random(3)).size()==5,"A question type may be zero");
            List<QuizCore.Question> shortTest=QuizCore.select(all,"基隆市",false,true,5,5,new Random(7));for(String city:QuizCore.CITIES)check(shortTest.stream().filter(q->q.city.equals(city)).count()==(city.equals("基隆市")?6:1),"10-question mixed-type test must preserve overall 60/10 distribution");
            try{QuizCore.select(all,"基隆市",false,false,0,0,new Random());throw new AssertionError("Empty exam accepted");}catch(IllegalArgumentException expected){}
            try{QuizCore.select(all,"基隆市",false,false,500,0,new Random());throw new AssertionError("Too many questions accepted");}catch(IllegalArgumentException expected){}
        }
        check(QuizCore.nextUnanswered(new int[]{0,-1,2,-1},3)==1,"At final question, skip answered questions and wrap");
        check(QuizCore.nextUnanswered(new int[]{0,1,2},2)==-1,"Completed exam");
        List<QuizCore.Question> first=all.subList(0,3);int[] right={first.get(0).answer,first.get(1).answer,first.get(2).answer};check(QuizCore.score(first,right)==100,"Correct answers score 100");
        String before="第一題。答案:是\n第二題。答案:否\n", after="新增題。答案:否\n"+before;
        check(QuizCore.parse("基隆市_地理環境_是非題.txt",before).get(0).id.equals(QuizCore.parse("基隆市_地理環境_是非題.txt",after).get(1).id),"Question identity survives inserts");
        try{QuizCore.parse("基隆市_地理環境_是非題.txt","沒有答案");throw new AssertionError("Malformed data accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: "+all.size()+" real questions; stable identities, parsing, weighted sampling, limits, scoring and skipped-question navigation.");
    }
}
