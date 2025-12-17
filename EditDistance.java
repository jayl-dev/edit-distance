import java.util.LinkedList;
import java.util.List;

public class BrewEditDistance{

  public static void main(String[] args) {
        String s = "New york", t = "newyork";
        BrewEditDistance ed = new BrewEditDistance(new CostFunction(){
          @Override
          public float getEditCost(Edit edit, int index) {
            return edit.move == Move.MATCH || edit.character == ' '? 0 : 1.0f;
          }
        });
        TraceBack trace = ed.brewEditDistance(s, t);
        EditResults res = constructResultFromTraceBack(trace);
        System.out.println(res.getEditPath());
        System.out.println(res.totalCost);
  }
   
  static enum Move{
    DELETE, INSERT, SUBSTITUTE, MATCH
  }

  static class Edit{
    public static final Edit INITIAL = new Edit(null, '0');
    Move move;
    char character;
    char subCharacter; //only available in Substitute case
    public Edit(Move move, char character) {
      this.move = move;
      this.character = character;
    }
   
    public Edit(Move move, char character, char subCharacter) {
      this.move = move;
      this.character = character;
      this.subCharacter = subCharacter;
    }
  
    @Override
    public String toString() {
      return move.name() + " " + character+ (move==Move.SUBSTITUTE? " with "+subCharacter:"");
    }
  }
 
  abstract static class CostFunction{
    public abstract float getEditCost(Edit edit, int index);  
  }
 
  static class EditResults{
    private float totalCost;
    private List<Edit> editPath = new LinkedList<Edit>();
    public float getTotalCost() {
      return totalCost;
    }
    public List<Edit> getEditPath() {
      return editPath;
    }
  
    public void setTotalCost(float totalCost) {
      this.totalCost = totalCost;
    }  
  }
 
  static class TraceBack {
    float cost;
    Edit edit;
    TraceBack prevTraceBack;
    public TraceBack(float cost, Edit edit, TraceBack nextTraceBack) {
      this.cost = cost;
      this.edit = edit;
      this.prevTraceBack = nextTraceBack;
    }
  }
 
  static EditResults constructResultFromTraceBack(TraceBack traceBack){
    EditResults ret =new EditResults();
    ret.setTotalCost(traceBack.cost);
    TraceBack t = traceBack;
    while(t.edit != Edit.INITIAL){
      ret.editPath.add(0, t.edit);
      t = t.prevTraceBack;
    }
    return ret;
  }
 
  public BrewEditDistance(CostFunction costFunction){
    this.costFunction = costFunction;
  }
 
  private CostFunction costFunction;
  private float threshold = Float.MAX_VALUE;

  public TraceBack brewEditDistance(String fromString, String toString) {
    if (toString == null || fromString == null) {
        throw new IllegalArgumentException("Strings must not be null");
    }
  
    //Apache common-langs's implementation always transform from t to s , which is very counter intuitive.
    //In their case it doesnt matter because it doesnt track edits and all edit costs are the same
    //but why the heck would anyone in the right mind want to call editDistance(s, t) and have it compute as
    //transform t to s? here I just substitute them back to what they are supposed to be meant
    int n = toString.length();
    int m = fromString.length();

    if (n == 0) { //toString is empty, so we are doing all deletes
      return constructTraceBack(fromString, costFunction, Move.DELETE);
    } else if (m == 0) {//fromString is empty, so we are doing all inserts
      return constructTraceBack(toString, costFunction, Move.INSERT);
    }

    //(see original apache common-lang getLevensteinDistance())
    //we cannot do swap strings memory optimization any more because insert/delete cost can be different
    //swapping the strings will temper the final edit cost. Swapping the strings will also screw up the edit path
    //however, in many applications your should have 2 similar length strings, otherwise
    //you can skip edit distance and just consider 2 strings that varies greatly in length
    //to be |s1.length - s2.length| which should be a good enough approximation
  
    TraceBack p[] = new TraceBack[n+1]; //'previous' cost array, horizontally
    TraceBack d[] = new TraceBack[n+1]; // cost array, horizontally
    TraceBack _d[]; //placeholder to assist in swapping p and d

    // indexes into strings toString and fromString
    int j; // iterates through toString
    int i; // iterates through fromString

    char from_i; // jth character of fromString
    char to_j; // ith character of toString

    Edit edit;
    p[0] = new TraceBack(0, Edit.INITIAL, null);
    for (j = 0; j<n; j++) {
      TraceBack prev = p[j];
      edit = new Edit(Move.INSERT, toString.charAt(j));
      p[j+1] = new TraceBack(prev.cost+costFunction.getEditCost(edit, j), edit, prev);
    }

    for (i = 1; i<=m; i++) {
        from_i = fromString.charAt(i-1);
        edit = new Edit(Move.DELETE, from_i);
        d[0] = new TraceBack(p[0].cost+costFunction.getEditCost(edit, i), edit, null);
        for (j=1; j<=n; j++) {
          to_j = toString.charAt(j-1);
            edit = new Edit(to_j==from_i ? Move.MATCH:Move.SUBSTITUTE, from_i, to_j);
            TraceBack sub = new TraceBack(costFunction.getEditCost(edit, i), edit, p[j-1]);
            edit = new Edit(Move.DELETE, from_i);
            TraceBack del = new TraceBack(costFunction.getEditCost(edit, i), edit, p[j]);
            edit = new Edit(Move.INSERT, to_j);
            TraceBack insert = new TraceBack(costFunction.getEditCost(edit, j), edit, d[j-1]);
            d[j] = best(sub, insert, del);
        }
        boolean stillInRange = false;
        for(int x =0; x<= n; x++){ //check to see if there still exist a path that is within the threshold range
            if(d[x].cost < threshold){
                stillInRange=true; break;
            }
        }
        if(!stillInRange){
            throw new RuntimeException("Threshold reached");
        }
        // copy current distance counts to 'previous row' distance counts
        _d = p;
        p = d;
        d = _d;
    }

    // our last action in the above loop was to switch d and p, so p now
    // actually has the most recent cost counts
    return p[n];
  }
 
  private static TraceBack constructTraceBack(String s, CostFunction costFunction, Move move){
    TraceBack trace = new TraceBack(0f, Edit.INITIAL, null);
    for(int i =0;i<s.length();i++){
      Edit edit = new Edit(move, s.charAt(i));
      trace = new TraceBack(costFunction.getEditCost(edit, i), edit, trace);
    }
    return trace;
  }
 
  private static TraceBack best(TraceBack substitute, TraceBack insert, TraceBack delete){
    float subCost = substitute.cost + substitute.prevTraceBack.cost;
    float intCost = insert.cost + insert.prevTraceBack.cost;
    float delCost = delete.cost + delete.prevTraceBack.cost;

    TraceBack ret = substitute;
    ret.cost = subCost;
    float bestCost = subCost;
  
    if(intCost < bestCost){
      bestCost = intCost;
      ret = insert;
    }
    if(delCost < bestCost){
      bestCost = delCost;
      ret = delete;
    }
    ret.cost = bestCost;
    return ret;
  }
 
}

