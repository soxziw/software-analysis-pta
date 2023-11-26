package pku;


import java.util.HashMap;

import pascal.taie.ir.IR;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.InvokeStatic;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.analysis.pta.core.cs.context.TrieContext.*;

import java.util.Map;

public class PreprocessResult {

    public final Map<New, Integer> obj_ids;
    public final Map<Integer, Var> test_pts;

    public PreprocessResult(){
        obj_ids = new HashMap<New, Integer>();
        test_pts = new HashMap<Integer,Var>();
        // heapModel = new AllocationSiteBasedModel(options);
        // heapModel = new HeapModel();
    }

    /**
     * Benchmark.alloc(id);
     * X x = new X;// stmt
     * @param stmt statement that allocates a new object
     * @param id id of the object allocated
     */
    public void alloc(New obj, int id)
    {
        obj_ids.put(obj, id);
    }
    /**
     * Benchmark.test(id, var)
     * @param id id of the testing
     * @param v the pointer/variable
     */
    public void test(int id, Var v)
    {
        test_pts.put(id, v);
    }
    /**
     *
     * @param stmt statement that allocates a new object
     * @return id of the object allocated
     */
    public int getObjIdAt(New obj)
    {
        return obj_ids.get(obj);
    }
    /**
     * @param id
     * @return the pointer/variable in Benchmark.test(id, var);
     */
    public Var getTestPt(int id)
    {
        return test_pts.get(id);
    }

    /**
     * analysis of a JMethod, the result storing in this
     * @param ir ir of a JMethod
     */
    public void analysis(IR ir) {
        var stmts = ir.getStmts();
        Integer id = 0;

        for (var stmt : stmts) {

            if(stmt instanceof Invoke)
            {
                var exp = ((Invoke) stmt).getInvokeExp();
                if(exp instanceof InvokeStatic)
                {
                    var methodRef = ((InvokeStatic)exp).getMethodRef();
                    var className = methodRef.getDeclaringClass().getName();
                    var methodName = methodRef.getName();
                    if(className.equals("benchmark.internal.Benchmark")
                    || className.equals("benchmark.internal.BenchmarkN"))
                    {
                        if(methodName.equals("alloc"))
                        {
                            var lit = exp.getArg(0).getConstValue();
                            assert lit instanceof IntLiteral;
                            id = ((IntLiteral)lit).getNumber();
                        }
                        else if(methodName.equals("test"))
                        {
                            var lit = exp.getArg(0).getConstValue();
                            assert lit instanceof IntLiteral;
                            var test_id = ((IntLiteral)lit).getNumber();
                            var pt = exp.getArg(1);
                            this.test(test_id, pt);
                        }
                    }

                }
            }
            else if(stmt instanceof New new_stmt)
            {
                if(id!=0) {// ignore unlabeled `new` stmts
                
                    this.alloc(new_stmt, id);
                    // workList.addEntry((New)stmt, new PointsToSet(obj));
                }
            }
        }
    }
}
