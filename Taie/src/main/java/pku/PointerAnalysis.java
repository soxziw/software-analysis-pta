package pku;


// import static pku.PointerAnalysisTrivial.logger; test

import java.util.TreeSet;

import pascal.taie.World;
import pascal.taie.analysis.ProgramAnalysis;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.DefaultCallGraph;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelectorFactory;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.PointerAnalysisResultImpl;
import pascal.taie.analysis.pta.core.cs.CSCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.cs.element.MapBasedCSManager;
import pascal.taie.analysis.pta.core.cs.element.Pointer;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.analysis.pta.core.heap.AllocationSiteBasedModel;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
// import pascal.taie.analysis.pta.core.solver.PointerFlowGraph;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.type.Type;

import pascal.taie.analysis.pta.pts.MyPointsToSetFactory;


import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.config.AnalysisOptions;
public class PointerAnalysis extends PointerAnalysisTrivial
{
    public static final String ID = "pku-pta";
    private CSManager csManager;
    private PointerFlowGraph pointerFlowGraph;
    private WorkList workList;
    private ContextSelector contextFactory;
    private PointsToSetFactory ptsFactory;
    private HeapModel heapModel;
    private CSCallGraph callGraph;
    public PointerAnalysis(AnalysisConfig config) {
        super(config);
    }


    private void addReachable(CSMethod csMethod) {
        // TODO - finish me
        Context context = csMethod.getContext();
        if ((callGraph.addReachableMethod(csMethod))) {
            var stmts = csMethod.getMethod().getIR().getStmts();
            for (Stmt stmt : stmts) {
                if (stmt instanceof New new_stmt) {
                    // 处理new语句
                    Obj obj = heapModel.getObj(new_stmt);
                    CSVar csVar = csManager.getCSVar(context, new_stmt.getLValue());
                    csVar = (CSVar) addPointsToSet(csVar);
                    CSObj csobj = csManager.getCSObj(contextFactory.selectHeapContext(csMethod, obj), obj);
                    workList.addEntry(csVar, ptsFactory.make(csobj));
                    //workList.addEntry(csVar, MyPointsToSetFactory.make(csobj));
                } else if (stmt instanceof Copy copy_stmt) {
                    // 处理 c:x = c:y
                    CSVar csVar_x = csManager.getCSVar(context, copy_stmt.getLValue());
                    csVar_x = (CSVar) addPointsToSet(csVar_x);
                    CSVar csVar_y = csManager.getCSVar(context, copy_stmt.getRValue());
                    csVar_y = (CSVar) addPointsToSet(csVar_y);
                    addPFGEdge(csVar_y, csVar_x);
                } else if (stmt instanceof StoreField storeField && storeField.isStatic()) {
                    // T.f = c:y
                    JField jField = storeField.getFieldAccess().getFieldRef().resolve();
                    Var y = storeField.getRValue();
                    addPFGEdge(addPointsToSet(csManager.getCSVar(context, y)), addPointsToSet(csManager.getStaticField(jField)));
                } else if (stmt instanceof LoadField loadField && loadField.isStatic()) {
                    // 处理 c:y = T.f
                    JField jField = loadField.getFieldAccess().getFieldRef().resolve();
                    Var y = loadField.getLValue();
                    addPFGEdge(addPointsToSet(csManager.getStaticField(jField)),addPointsToSet(csManager.getCSVar(context, y)));
                } else if (stmt instanceof Invoke invoke && invoke.isStatic()) {
                    // 处理静态方法调用，c:x = T.m()
                    JMethod m = resolveCallee(null, invoke);
                    CallKind callKind = CallGraphs.getCallKind(invoke);
                    assert callKind == CallKind.STATIC;
                    CSCallSite csCallSite = csManager.getCSCallSite(context, invoke);
                    Context context_t = contextFactory.selectContext(csCallSite, m);
                    if (callGraph.addEdge(new Edge<>(callKind, csCallSite, csManager.getCSMethod(context_t, m)))) {
                        addReachable(csManager.getCSMethod(context_t, m));
                        processHelper(context, context_t, m, invoke);
                    }
                }
            }
        }
    }

    private JMethod resolveCallee(CSObj recv, Invoke callSite) {
        Type type = recv != null ? recv.getObject().getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    private void processHelper(Context prev, Context cur, JMethod m, Invoke invoke) {
        // invoke -> m
        var args = invoke.getInvokeExp().getArgs();
        for (int i = 0; i < args.size(); i++) {
            Var param = m.getIR().getParam(i);
            Var arg = args.get(i);
            addPFGEdge(addPointsToSet(csManager.getCSVar(prev, arg)), addPointsToSet(csManager.getCSVar(cur, param)));
        }
        // handle return edge
        m.getIR().getReturnVars();
        if (invoke.getLValue() != null) {
            Var r = invoke.getLValue();
            for (Var ret : m.getIR().getReturnVars()) {
                addPFGEdge(addPointsToSet(csManager.getCSVar(cur, ret)), addPointsToSet(csManager.getCSVar(prev, r)));
            }
        }
    }
    @Override
    public PointerAnalysisResult analyze() {
        AnalysisOptions options = getOptions();
        heapModel = new AllocationSiteBasedModel(options);
        var result = new PointerAnalysisResult();
        var preprocess = new PreprocessResult();
        var world = World.get();

        workList = new WorkList();
        csManager = new MapBasedCSManager();
        callGraph = new CSCallGraph(csManager);
        pointerFlowGraph = new PointerFlowGraph();
        ptsFactory = new PointsToSetFactory(csManager.getObjectIndexer());
        contextFactory = ContextSelectorFactory.makePlainSelector(options.getString("cs"));

        CSMethod csMethod = csManager.getCSMethod(contextFactory.getEmptyContext(), world.getMainMethod());
        callGraph.addEntryMethod(csMethod);
        addReachable(csMethod);
        // var jclass = main.getDeclaringClass();

        // TODO
        // You need to use `preprocess` like in PointerAnalysisTrivial
        // when you enter one method to collect infomation given by
        // Benchmark.alloc(id) and Benchmark.test(id, var)
        //
        // As for when and how you enter one method,
        // it's your analysis assignment to accomplish


        World.get().getClassHierarchy().applicationClasses().forEach(jclass->{
             //logger.info("Analyzing class {}", jclass.getName());
             jclass.getDeclaredMethods().forEach(method->{
                 if(!method.isAbstract())
                     preprocess.analysis(method.getIR());
             });
         });


        callGraph.entryMethods().forEach(this::addReachable);
        while (!workList.isEmpty()) {
            //WorkList.PointerEntry entry = (WorkList.PointerEntry)workList.pollEntry();
            WorkList.Entry entry = workList.pollEntry();
            Pointer n = entry.pointer();
            PointsToSet pts = entry.pointsToSet();
            assert n.getPointsToSet() != null;
            PointsToSet delta = propagate(n, pts);
            if (delta.isEmpty()) continue;
            if (n instanceof CSVar n_ptr) {
                Var x = n_ptr.getVar();
                Context context = n_ptr.getContext();
                for (CSObj obj : delta) {
                    for (StoreField storeField : x.getStoreFields()) {
                        // c':o.f = c:y
                        Var y = storeField.getRValue();
                        CSVar y_ptr = csManager.getCSVar(context, y);
                        y_ptr = (CSVar) addPointsToSet(y_ptr);
                        InstanceField instanceField = csManager.getInstanceField(obj, storeField.getFieldRef().resolve());
                        instanceField = (InstanceField) addPointsToSet(instanceField);
                        addPFGEdge(y_ptr, instanceField);
                    }
                    for (LoadField loadField : x.getLoadFields()) {
                        // c:y = c':o.f
                        Var y = loadField.getLValue();
                        CSVar y_ptr = csManager.getCSVar(context, y);
                        y_ptr = (CSVar) addPointsToSet(y_ptr);
                        InstanceField instanceField = csManager.getInstanceField(obj, loadField.getFieldRef().resolve());
                        instanceField = (InstanceField) addPointsToSet(instanceField);
                        addPFGEdge(instanceField, y_ptr);
                    }

                    for (StoreArray storeArray : x.getStoreArrays()) {
                        // 处理Array Store c':x[i] = c:y
                        Var y = storeArray.getRValue();
                        CSVar y_ptr = csManager.getCSVar(context, y);
                        y_ptr = (CSVar) addPointsToSet(y_ptr);
                        ArrayIndex arrayIndex = csManager.getArrayIndex(obj);
                        arrayIndex = (ArrayIndex) addPointsToSet(arrayIndex);
                        addPFGEdge(y_ptr, arrayIndex);
                    }

                    for (LoadArray loadArray : x.getLoadArrays()) {
                        // 处理Load Array c:y = c':x[i]
                        Var y = loadArray.getLValue();
                        CSVar y_ptr = csManager.getCSVar(context, y);
                        y_ptr = (CSVar) addPointsToSet(y_ptr);
                        ArrayIndex arrayIndex = csManager.getArrayIndex(obj);
                        arrayIndex = (ArrayIndex) addPointsToSet(arrayIndex);
                        addPFGEdge(arrayIndex, y_ptr);
                    }
                    processCall(n_ptr, obj);
                }
            }

        }


        preprocess.test_pts.forEach((test_id, pt)->{
            PointsToSet sum_pts = ptsFactory.make();
            //PointsToSet sum_pts = MyPointsToSetFactory.make();
            csManager.getCSVarsOf(pt).forEach((csVar)->{
                sum_pts.addAll(csVar.getPointsToSet());
            });
            TreeSet<Integer> pts_id = new TreeSet<Integer>();
            sum_pts.forEach((obj)->{
                pts_id.add(preprocess.getObjIdAt((New)(obj.getObject().getAllocation())));
            });
            result.put(test_id, pts_id);
        });

        System.out.println(result.toString());
        dump(result);
        return result;
        // return result;
    }

    private void processCall(CSVar recv, CSObj recvObj) {
        // TODO - finish me
        // c:x : c':o_i
        Context context = recv.getContext();
        for (Invoke invoke : recv.getVar().getInvokes()) {
            JMethod m = resolveCallee(recvObj, invoke);
            CSCallSite csCallSite = csManager.getCSCallSite(context, invoke);
            // resolve callee context
            Context context_t = contextFactory.selectContext(csCallSite, recvObj, m);
            CallKind callKind = CallGraphs.getCallKind(invoke);
            CSMethod csMethod = csManager.getCSMethod(context_t, m);
            if (m.getIR().getThis() != null) {
                // i.e instance method call
                workList.addEntry(addPointsToSet(csManager.getCSVar(context_t, m.getIR().getThis())), ptsFactory.make(recvObj));
                //workList.addEntry(addPointsToSet(csManager.getCSVar(context_t, m.getIR().getThis())), MyPointsToSetFactory.make(recvObj));
            }
            if (callGraph.addEdge(new Edge<>(callKind, csCallSite, csMethod))) {
                addReachable(csMethod);
                processHelper(context, context_t, m, invoke);
            }
        }
    }

    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
        // TODO - finish me
        PointsToSet delta = ptsFactory.make();
        //PointsToSet delta = MyPointsToSetFactory.make();
        PointsToSet pointer_set = pointer.getPointsToSet();
        for (CSObj obj : pointsToSet) {
            if (pointer_set.contains(obj)) continue;
            pointer_set.addObject(obj);
            delta.addObject(obj);
        }
        if (!delta.isEmpty()) {
            for (Pointer s : pointerFlowGraph.getSuccsOf(pointer)) {
                workList.addEntry(addPointsToSet(s), delta);//error there 查看pointerFlowGraph加进去的时候那个是null
            }
        }
        return delta;
    }

    private void addPFGEdge(Pointer source, Pointer target) {
        // TODO - finish me
        if (pointerFlowGraph.addEdge(source, target)) {
            if (!source.getPointsToSet().isEmpty()) {
                workList.addEntry(target, source.getPointsToSet());
            }
        }
    }

    private Pointer addPointsToSet(Pointer p) {
        // TODO - finish me
        if(p.getPointsToSet() == null){
            p.setPointsToSet(ptsFactory.make());
        }
        return p;
    }
}
