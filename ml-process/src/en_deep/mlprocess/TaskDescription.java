/*
 *  Copyright (c) 2009 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess;

import en_deep.mlprocess.Task.TaskStatus;
import java.io.Serializable;
import java.util.Vector;
import java.util.Hashtable;

/**
 *
 * @author Ondrej Dusek
 */
public class TaskDescription implements Serializable {

    /* CONSTANTS */

    
    /* DATA */

    /** The task global ID */
    private String id;
    /** The current task status */
    private TaskStatus status;

    /** The task algorithm class name */
    private String algorithm;
    /** The task algorithm parameters */
    private Hashtable<String, String> parameters;
    /** The needed input files */
    private Vector<String> input;
    /** The output files generated by this Task */
    private Vector<String> output;

    /** Topological order of the task (-1 if not sorted) */
    private int topolOrder;

    /** All the Tasks that this Task depends on */
    private Vector<TaskDescription> iDependOn;
    /** All the Task that are depending on this one */
    private Vector<TaskDescription> dependOnMe;

    /** This serves for generating tasks id in parallelization */
    private static int lastId = 0;


    /* METHODS */

    /**
     * Creates a new TaskDescription, given the task id, algorithm, parameters,
     * inputs and outputs. Inputs and outputs specifications must be relative
     * to the {@link Process} working directory.
     *
     * @param type the type of this task.
     */
    public TaskDescription(String id, String algorithm, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output){

        this.id = TaskDescription.generateId(id);
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.input = input;
        this.output = output;
        this.status = TaskStatus.PENDING; // no dependencies, yet
        this.topolOrder = -1; // not yet sorted
    }

    /**
     * Creates a copy of the given task description with a given id suffix.
     * All the members except algorithm are cloned, i.e. not just their references copied.
     *
     * @param other the object to be copied
     * @param idSuffix a suffix to be pasted at the end of the original id in order to distinguish the two
     */
    private TaskDescription(TaskDescription other, String idSuffix){

        this.id = other.id + '#' + idSuffix;
        this.algorithm = other.algorithm;
        this.parameters = (Hashtable<String,String>) other.parameters.clone();
        this.input = (Vector<String>) other.input.clone();
        this.output = (Vector<String>) other.output.clone();
        this.status = other.status;
        this.topolOrder = other.topolOrder;
        this.dependOnMe = (other.dependOnMe != null ? (Vector<TaskDescription>) other.dependOnMe.clone() : null);
        this.iDependOn = (other.iDependOn != null ? (Vector<TaskDescription>) other.iDependOn.clone() : null);
    }


    /**
     * Sets a dependency for this task (i.e\. marks this {@link TaskDescription} as depending
     * on the parameter). Checks for duplicate dependencies, i.e. a dependency from task A to
     * task B is stored only once, even if it is enforced multiple times. Sets the task status
     * to waiting - the dependent task needs to be processed first.
     *
     * @param source the governing {@link TaskDescription} that must be processed before this one.
     */
    void setDependency(TaskDescription source) {

        // if we have a dependency, we need to wait for it to finish
        this.status = TaskStatus.WAITING;

        if (this.iDependOn == null){
            this.iDependOn = new Vector<TaskDescription>();
        }
        if (!this.iDependOn.contains(source)){
            this.iDependOn.add(source);
        }

        if (source.dependOnMe == null){
            source.dependOnMe = new Vector<TaskDescription>();
        }
        if (!source.dependOnMe.contains(this)){
            source.dependOnMe.add(this);
        }
    }

    /**
     * Returns the current task progress status.
     * @return the current task status
     */
    public TaskStatus getStatus(){
        return this.status;
    }

    /**
     * Returns the task topological order (zero-based), or -1 if not yet sorted.
     * @return the topological order of the task
     */
    public int getOrder(){
        return this.topolOrder;
    }

    /**
     * Sets the task's topological order (as done in task topological sorting in
     * {@link Plan.sortPlan(Vector<TaskDescription> Plan)}).
     *
     * @param order the topological order for the task
     */
    void setOrder(int order){
        this.topolOrder = order;
    }

    /**
     * Returns a list of all dependent tasks, or null if there are none.
     * @return a list of all tasks depending on this one
     */
    Vector<TaskDescription> getDependent(){

        if (this.dependOnMe == null){
            return null;
        }
        return (Vector<TaskDescription>) this.dependOnMe.clone();
    }


    /**
     * Returns the unique ID for this task, composed of the name for the task
     * section in the input scenario file and a unique number.
     *
     * @return the generated ID string
     */
    private static synchronized String generateId(String prefix){

        String taskId;

        lastId++;
        taskId = prefix + "["+ lastId + "]";

        return taskId;
    }

    /**
     * Returns true if all the tasks on which this task depends are already topologically
     * sorted, i.e. their {@link topolOrder} is >= 0. If we have no prerequisities, "all of
     * them are sorted".
     *
     * @return the sorting status of the prerequisities tasks
     */
    boolean allPrerequisitiesSorted() {

        if (this.iDependOn == null){ // if there are none, they're all sorted.
            return true;
        }
        for (TaskDescription prerequisity : this.iDependOn){
            if (prerequisity.getOrder() < 0){
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the task's algorithm class name.
     * @return the name of the algorithm for this task
     */
    String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the ID of this task.
     * @return the task's id.
     */
    String getId(){
        return this.id;
    }

    /**
     * Returns the parameters for the algorithm.
     * @return the task algorithm parameters
     */
    Hashtable<String, String> getParameters() {
        return this.parameters;
    }

    /**
     * Returns the needed input files for this Task
     * @return the input files
     */
    Vector<String> getInput() {
        return this.input;
    }


    /**
     * Returns the generated output files for this Task
     * @return the output files
     */
    Vector<String> getOutput() {
        return this.output;
    }


    /**
     * Sets new task status, updating all the statuses of the dependent tasks (if the
     * new status is {@link TaskStatus.DONE}.
     * @param taskStatus the new task status
     */
    void setStatus(TaskStatus status) {

        this.status = status;
        
        // if we're done, update the depending tasks (if there are no other tasks
        // they've been waiting for, set their status to pending)
        if (status == TaskStatus.DONE){
            if (this.dependOnMe == null){
                return;
            }
            for (TaskDescription dependentTask : this.dependOnMe){

                if (dependentTask.status == TaskStatus.WAITING){

                    boolean otherPrerequisityNotDone = false;

                    for (TaskDescription prerequisity : dependentTask.iDependOn){
                        if (prerequisity.status != TaskStatus.DONE){
                            otherPrerequisityNotDone = true;
                            break;
                        }
                    }

                    if (!otherPrerequisityNotDone){
                        dependentTask.status = TaskStatus.PENDING;
                    }
                }
            }
        }
    }


    /**
     * Creates a copy of this task with input file patterns "*" expanded for
     * the given string. All the other parameters, including dependencies, are preserved.
     *
     * @param expPat the pattern expansion to be used
     * @return
     */
    public TaskDescription expand(String expPat) {
        
        TaskDescription copy = new TaskDescription(this, expPat);

        for (int i = 0; i < copy.input.size(); ++i){

            String elem = copy.input.get(i);
            int pos = elem.indexOf("*");
            if (pos != -1 && pos == elem.lastIndexOf("*")){
                elem = elem.substring(0, pos) + expPat
                        + (elem.length() < pos - 1 ? elem.substring(pos + 1) : "");
            }
            copy.input.set(i, elem);
        }

        return copy;
    }


    /**
     * Creates a copy of this task the input file pattern "***" at the given position expanded for
     * the given string. All the other parameters, including dependencies, are preserved.
     * If there's no "***" at the given position in the inputs, just a copy is returned.
     *
     * @param expPat the pattern expansion to be used
     * @return
     */
    public TaskDescription expand(String expPat, int inputNo){

        TaskDescription copy = new TaskDescription(this, expPat);
        String elem = copy.input.get(inputNo);
        int pos = elem.indexOf("***");

        if (pos != -1 && pos == elem.lastIndexOf("***")){
            elem = elem.substring(0, pos) + expPat
                    + (elem.length() < pos - 1 ? elem.substring(pos + 1) : "");
        }
        copy.input.set(inputNo, elem);

        return copy;
    }


    /**
     * Looses dependencies to all tasks whose ids match the given prefix.
     * @param idPrefix the prefix to drop dependency to
     */
    public void looseDeps(String idPrefix) {

        if (idPrefix == null){ // null parameter -- remove all dependencies
            idPrefix = "";
        }

        // backward dependencies
        if (this.iDependOn != null){

            for(int i = this.iDependOn.size() - 1; i <= 0; ++i){
                if (this.iDependOn.get(i).getId().startsWith(idPrefix)){

                    TaskDescription dep = this.iDependOn.remove(i);
                    dep.dependOnMe.remove(this);
                }
            }
            if (this.iDependOn.size() == 0){
                this.iDependOn = null;
            }
        }
        // forward dependencies
        if (this.dependOnMe != null){

            for (int i = this.dependOnMe.size() - 1; i <= 0; ++i){
                if (this.dependOnMe.get(i).getId().startsWith(idPrefix)){

                    TaskDescription dep = this.dependOnMe.remove(i);
                    dep.dependOnMe.remove(this);
                }
            }
            if (this.dependOnMe.size() == 0){
                this.dependOnMe = null;
            }
        }
    }


    /**
     * Unbinds the task from all dependencies completely.
     */
    public void looseAllDeps(){
        this.looseDeps(null);
    }

    @Override
    public String toString() {

        StringBuilder iDO = new StringBuilder();
        StringBuilder dOM = new StringBuilder();

        if (this.iDependOn != null){
            for (TaskDescription td : this.iDependOn){
                iDO.append(td.id + " ");
            }
        }
        if (this.dependOnMe != null){
            for (TaskDescription td : this.dependOnMe){
                dOM.append(td.id + " ");
            }
        }

        return this.id + ": " + this.status + "\n" + "\tparams: " + this.parameters.toString()
                + "\n\tiDependOn: " + iDO.toString() + "\n\tdependOnMe: " + dOM.toString()
                + "\n\tinput: " + this.input.toString() + "\n\toutput:" + this.output.toString() + "\n";
    }



}
