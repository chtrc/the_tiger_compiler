package slp;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.HashSet;

import slp.Slp.Exp;
import slp.Slp.Exp.Eseq;
import slp.Slp.Exp.Id;
import slp.Slp.Exp.Num;
import slp.Slp.Exp.Op;
import slp.Slp.ExpList;
import slp.Slp.Stm;
import util.Bug;
import util.Todo;
import control.Control;

public class Main
{
  // ///////////////////////////////////////////
  // maximum number of args

  private int maxArgsExp(Exp.T exp)
  {
    if(exp instanceof Exp.Id || 
    		exp instanceof Exp.Num) {
    	return 1;
    } else if(exp instanceof Exp.Op) {
    	int n1 = maxArgsExp(((Exp.Op)exp).left);
    	int n2 = maxArgsExp(((Exp.Op)exp).right);
    	return n1 > n2 ? n1 : n2;
    }else if(exp instanceof Exp.Eseq) {
    	int n1 = maxArgsStm(((Exp.Eseq)exp).stm);
    	int n2 = maxArgsExp(((Exp.Eseq)exp).exp);
    	return java.lang.Math.max(java.lang.Math.max(2,n1),n2);
    } else {
    	new Bug();
    	return 0;
    }
  }

  private int maxArgsStm(Stm.T stm)
  {
    if (stm instanceof Stm.Compound) {
      Stm.Compound s = (Stm.Compound) stm;
      int n1 = maxArgsStm(s.s1);
      int n2 = maxArgsStm(s.s2);

      return n1 >= n2 ? n1 : n2;
    } else if (stm instanceof Stm.Assign) {
      return maxArgsExp(((Stm.Assign) stm).exp);
    } else if (stm instanceof Stm.Print) {
    	int cnt = 0;
    	int max = 0;
    	ExpList.T p = ((Stm.Print) stm).explist;
    	while(p instanceof ExpList.Pair) {
    		if(maxArgsExp(((ExpList.Pair) p).exp)>max) {
    			max = maxArgsExp(((ExpList.Pair) p).exp);
    		}
    		++cnt;
    		p = ((ExpList.Pair) p).list;
    	}
    	if(p instanceof ExpList.Last) {
    		if(maxArgsExp(((ExpList.Last) p).exp)>max) {
    			max = maxArgsExp(((ExpList.Last) p).exp);
    		}
    		++cnt;
    	}
    	return max > cnt ? max : cnt;
      
    } else
      new Bug();
    return 0;
  }

  // ////////////////////////////////////////
  // interpreter

  private class Table {
	  private String id;
	  int value;
	  private Table tail = null;
	  Table(String id, int value, Table tail){
		  this.id = id;
		  this.value = value;
		  this.tail = tail;
	  }
  }
  private Table statement = null;
  private int lookUp(String id)
  {
	  Table t = statement;
	  while(t != null) {
		  if(t.id == id) {
			  return t.value;
		  }
		  t = t.tail;
	  }
	  new Bug();
	  return -1;
  }
  private int interpExp(Exp.T exp)
  {
    if(exp instanceof Exp.Id) {
    	return lookUp(((Exp.Id)exp).id);
    } else if(exp instanceof Exp.Num) {
    	return ((Exp.Num)exp).num;
    } else if (exp instanceof Exp.Op) {
    	Exp.Op op = (Exp.Op)exp;
    	if(op.op == Exp.OP_T.ADD) {
    		return interpExp(op.left) + interpExp(op.right);
    	} else if(op.op == Exp.OP_T.SUB) {
    		return interpExp(op.left) - interpExp(op.right);
    	} else if(op.op == Exp.OP_T.TIMES) {
    		return interpExp(op.left) * interpExp(op.right);
    	} else if(op.op == Exp.OP_T.DIVIDE) {
    		if(interpExp(op.right) == 0) {
    			System.out.println("error: divide 0!");
    			new Bug();
    			return -1;
    		} else {
    			return interpExp(op.left) / interpExp(op.right);
    		}
    	} else {
    		new Bug();
    		return -1;
    	}
    } else if(exp instanceof Exp.Eseq) {
    	Exp.Eseq e = (Exp.Eseq)exp;
    	interpStm(e.stm);
    	return interpExp(e.exp);
    } else {
    	new Bug();
    	return -1;
    }
  }

  private void interpStm(Stm.T prog)
  {
    if (prog instanceof Stm.Compound) {
      Stm.Compound s = (Stm.Compound)prog;
      interpStm(s.s1);
      interpStm(s.s2);
      return;
    } else if (prog instanceof Stm.Assign) {
      Stm.Assign s = (Stm.Assign)prog;
      String id = s.id;
      int expValue = interpExp(s.exp);
      
      if(statement != null) {
    	  Table t = new Table(id, expValue, statement);
    	  statement = t;
      } else {
    	  statement = new Table(id, expValue, null);
      }
      
      return;
    } else if (prog instanceof Stm.Print) {
      Stm.Print s = (Stm.Print) prog;
      ExpList.T t = s.explist;
      while(t instanceof ExpList.Pair) {
    	  ExpList.Pair p = (ExpList.Pair)t;
    	  System.out.print(interpExp(p.exp) + " ");
    	  t = p.list;
      }
      if(t instanceof ExpList.Last) {
    	  ExpList.Last p = (ExpList.Last)t;
    	  System.out.print(interpExp(p.exp));
    	  System.out.print("\n");
      } else {
    	  new Bug();
      }
    } else
      new Bug();
  }

  // ////////////////////////////////////////
  // compile
  HashSet<String> ids;
  StringBuffer buf;

  private void emit(String s)
  {
    buf.append(s);
  }

  private void compileExp(Exp.T exp)
  {
    if (exp instanceof Id) {
      Exp.Id e = (Exp.Id) exp;
      String id = e.id;

      emit("\tmovl\t" + id + ", %eax\n");
    } else if (exp instanceof Num) {
      Exp.Num e = (Exp.Num) exp;
      int num = e.num;

      emit("\tmovl\t$" + num + ", %eax\n");
    } else if (exp instanceof Op) {
      Exp.Op e = (Exp.Op) exp;
      Exp.T left = e.left;
      Exp.T right = e.right;
      Exp.OP_T op = e.op;

      switch (op) {
      case ADD:
        compileExp(left);
        emit("\tpushl\t%eax\n");
        compileExp(right);
        emit("\tpopl\t%edx\n");
        emit("\taddl\t%edx, %eax\n");
        break;
      case SUB:
        compileExp(left);
        emit("\tpushl\t%eax\n");
        compileExp(right);
        emit("\tpopl\t%edx\n");
        emit("\tsubl\t%eax, %edx\n");
        emit("\tmovl\t%edx, %eax\n");
        break;
      case TIMES:
        compileExp(left);
        emit("\tpushl\t%eax\n");
        compileExp(right);
        emit("\tpopl\t%edx\n");
        emit("\timul\t%edx\n");
        break;
      case DIVIDE:
        compileExp(left);
        emit("\tpushl\t%eax\n");
        compileExp(right);
        emit("\tpopl\t%edx\n");
        emit("\tmovl\t%eax, %ecx\n");
        emit("\tmovl\t%edx, %eax\n");
        emit("\tcltd\n");
        emit("\tdiv\t%ecx\n");
        break;
      default:
        new Bug();
      }
    } else if (exp instanceof Eseq) {
      Eseq e = (Eseq) exp;
      Stm.T stm = e.stm;
      Exp.T ee = e.exp;

      compileStm(stm);
      compileExp(ee);
    } else
      new Bug();
  }

  private void compileExpList(ExpList.T explist)
  {
    if (explist instanceof ExpList.Pair) {
      ExpList.Pair pair = (ExpList.Pair) explist;
      Exp.T exp = pair.exp;
      ExpList.T list = pair.list;

      compileExp(exp);
      emit("\tpushl\t%eax\n");
      emit("\tpushl\t$slp_format\n");
      emit("\tcall\tprintf\n");
      emit("\taddl\t$4, %esp\n");
      compileExpList(list);
    } else if (explist instanceof ExpList.Last) {
      ExpList.Last last = (ExpList.Last) explist;
      Exp.T exp = last.exp;

      compileExp(exp);
      emit("\tpushl\t%eax\n");
      emit("\tpushl\t$slp_format\n");
      emit("\tcall\tprintf\n");
      emit("\taddl\t$4, %esp\n");
    } else
      new Bug();
  }

  private void compileStm(Stm.T prog)
  {
    if (prog instanceof Stm.Compound) {
      Stm.Compound s = (Stm.Compound) prog;
      Stm.T s1 = s.s1;
      Stm.T s2 = s.s2;

      compileStm(s1);
      compileStm(s2);
    } else if (prog instanceof Stm.Assign) {
      Stm.Assign s = (Stm.Assign) prog;
      String id = s.id;
      Exp.T exp = s.exp;

      ids.add(id);
      compileExp(exp);
      emit("\tmovl\t%eax, " + id + "\n");
    } else if (prog instanceof Stm.Print) {
      Stm.Print s = (Stm.Print) prog;
      ExpList.T explist = s.explist;

      compileExpList(explist);
      emit("\tpushl\t$newline\n");
      emit("\tcall\tprintf\n");
      emit("\taddl\t$4, %esp\n");
    } else
      new Bug();
  }

  // ////////////////////////////////////////
  public void doit(Stm.T prog)
  {
    // return the maximum number of arguments
    if (Control.ConSlp.action == Control.ConSlp.T.ARGS) {
      int numArgs = maxArgsStm(prog);
      System.out.println(numArgs);
    }

    // interpret a given program
    if (Control.ConSlp.action == Control.ConSlp.T.INTERP) {
      interpStm(prog);
    }

    // compile a given SLP program to x86
    if (Control.ConSlp.action == Control.ConSlp.T.COMPILE) {
      ids = new HashSet<String>();
      buf = new StringBuffer();

      compileStm(prog);
      try {
        // FileOutputStream out = new FileOutputStream();
        FileWriter writer = new FileWriter("slp_gen.s");
        writer
            .write("// Automatically generated by the Tiger compiler, do NOT edit.\n\n");
        writer.write("\t.data\n");
        writer.write("slp_format:\n");
        writer.write("\t.string \"%d \"\n");
        writer.write("newline:\n");
        writer.write("\t.string \"\\n\"\n");
        for (String s : this.ids) {
          writer.write(s + ":\n");
          writer.write("\t.int 0\n");
        }
        writer.write("\n\n\t.text\n");
        writer.write("\t.globl main\n");
        writer.write("main:\n");
        writer.write("\tpushl\t%ebp\n");
        writer.write("\tmovl\t%esp, %ebp\n");
        writer.write(buf.toString());
        writer.write("\tleave\n\tret\n\n");
        writer.close();
        Process child = Runtime.getRuntime().exec("gcc slp_gen.s");
        child.waitFor();
        if (!Control.ConSlp.keepasm)
          Runtime.getRuntime().exec("rm -rf slp_gen.s");
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(0);
      }
      // System.out.println(buf.toString());
    }
  }
}
