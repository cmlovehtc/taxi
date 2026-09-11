package tw.cmlove.taxiexam;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** One atomic journal keeps completed sessions, rewards and progress consistent. */
public final class StudyState {
    public JSONObject data;
    public Session active;
    public final Set<String> wrong=new HashSet<>(), starred=new HashSet<>(), earned=new HashSet<>(), owned=new HashSet<>();
    private final File file;
    public boolean recovered=false;
    public StudyState(Context context)throws Exception {
        file=new File(context.getFilesDir(),"study-state.json");
        try {data=new JSONObject(BankStore.read(new AtomicFile(file).openRead(),15*1024*1024));loadData();}
        catch(FileNotFoundException e){data=new JSONObject();loadData();}
        catch(Exception e){recovered=true;if(file.exists())java.nio.file.Files.copy(file.toPath(),new File(file.getParentFile(),"study-state-unreadable-"+System.currentTimeMillis()+".json").toPath());data=new JSONObject();loadData();}
    }
    private void loadSet(String key,Set<String> target) {target.clear();JSONArray array=data.optJSONArray(key);if(array!=null)for(int i=0;i<array.length();i++)target.add(array.optString(i));}
    private void loadData()throws Exception {
        if(data.optInt("schema",1)!=1)throw new IOException("不支援的備份版本");
        loadSet("wrong",wrong);loadSet("starred",starred);loadSet("earned",earned);loadSet("owned",owned);
        active=data.optJSONObject("active")==null?null:new Session(data.getJSONObject("active"));
    }
    public void save()throws Exception {
        data.put("schema",1).put("app","tw.cmlove.taxiexam.preview").put("wrong",new JSONArray(wrong)).put("starred",new JSONArray(starred)).put("earned",new JSONArray(earned)).put("owned",new JSONArray(owned));
        data.put("active",active==null?JSONObject.NULL:active.toJson());
        BankStore.writeAtomic(file,data.toString());
    }
    public void restore(JSONObject snapshot)throws Exception {
        if(snapshot.optInt("schema")!=1 || !snapshot.optString("app").equals("tw.cmlove.taxiexam.preview"))throw new IOException("不支援的備份檔");
        if(snapshot.optInt("coins",0)<0 || snapshot.optInt("coins",0)>10000000 || !snapshot.optString("carColor","#FFD33D").matches("#[0-9a-fA-F]{6}"))throw new IOException("備份資料不完整");
        JSONObject previous=data;try{data=snapshot;loadData();save();}catch(Exception e){data=previous;loadData();throw e;}
    }
    public JSONArray history() {JSONArray h=data.optJSONArray("history");return h==null?new JSONArray():h;}
    public JSONObject finish()throws Exception {
        if(active==null)throw new IllegalStateException("沒有測驗");
        Session s=active;int correct=0,unanswered=0,newlyEarned=0;
        for(int i=0;i<s.questions.size();i++) {QuizCore.Question q=s.questions.get(i);if(s.answers[i]<0)unanswered++;
            if(s.answers[i]==q.answer){correct++;wrong.remove(q.id);if(earned.add(q.id))newlyEarned++;}else wrong.add(q.id);
        }
        JSONObject record=new JSONObject().put("id",s.id).put("title",s.title).put("time",System.currentTimeMillis()).put("correct",correct).put("total",s.questions.size()).put("score",QuizCore.score(s.questions,s.answers)).put("unanswered",unanswered).put("reward",newlyEarned*3);
        JSONArray history=new JSONArray().put(record),previous=history();for(int i=0;i<Math.min(previous.length(),99);i++)history.put(previous.get(i));
        data.put("history",history).put("coins",data.optInt("coins",0)+newlyEarned*3).put("attempts",data.optInt("attempts",0)+s.questions.size()).put("correct",data.optInt("correct",0)+correct);
        active=null;save();return record;
    }
    public static JSONObject qJson(QuizCore.Question q)throws Exception {return new JSONObject().put("id",q.id).put("file",q.file).put("city",q.city).put("text",q.text).put("tf",q.tf).put("number",q.number).put("answer",q.answer).put("choices",q.choices);}
    public static QuizCore.Question readQuestion(JSONObject q)throws Exception {
        int choices=q.getInt("choices"), answer=q.getInt("answer");boolean tf=q.getBoolean("tf");
        if(choices<2||choices>4||answer<0||answer>=choices||(tf&&choices!=2))throw new IOException("不正確的題目資料");
        return new QuizCore.Question(q.getString("id"),q.getString("file"),q.getString("city"),q.getString("text"),tf,q.getInt("number"),answer,choices);
    }
    public static final class Session {
        public final String id,title;
        public final List<QuizCore.Question> questions;
        public final int[] answers;
        public final boolean exam;
        public final long started,deadline;
        public int index;
        public Session(List<QuizCore.Question> q,boolean exam,int minutes,String title) {id=UUID.randomUUID().toString();this.title=title;questions=new ArrayList<>(q);answers=new int[q.size()];Arrays.fill(answers,-1);this.exam=exam;started=System.currentTimeMillis();deadline=exam?started+minutes*60000L:0;}
        Session(JSONObject s)throws Exception {
            id=s.getString("id");title=s.getString("title");exam=s.getBoolean("exam");started=s.getLong("started");deadline=s.getLong("deadline");
            JSONArray q=s.getJSONArray("questions"),a=s.getJSONArray("answers");if(q.length()<1||q.length()>500||a.length()!=q.length())throw new IOException("測驗紀錄不完整");
            questions=new ArrayList<>();answers=new int[q.length()];for(int i=0;i<q.length();i++){questions.add(readQuestion(q.getJSONObject(i)));answers[i]=a.getInt(i);if(answers[i]<-1||answers[i]>=questions.get(i).choices)throw new IOException("作答紀錄不完整");}
            index=Math.max(0,Math.min(s.optInt("index"),q.length()-1));
        }
        JSONObject toJson()throws Exception {JSONArray q=new JSONArray(),a=new JSONArray();for(QuizCore.Question item:questions)q.put(qJson(item));for(int answer:answers)a.put(answer);return new JSONObject().put("id",id).put("title",title).put("exam",exam).put("started",started).put("deadline",deadline).put("index",index).put("questions",q).put("answers",a);}
    }
}
