package AngryPigs

import org.lwjgl.opengl.{Display,PixelFormat,DisplayMode,Util}
import org.lwjgl.input.Keyboard
import scala.collection.mutable.ListBuffer
import java.nio._
import scala.actors.Futures._
import scala.actors.Future

// TODO:
// search for @ <task>

// longterm: 
// search for /// <task>
/// decouple physics and render
/// infinite terrain patches and stuff

object Game {
    import Global._
    import org.lwjgl.opengl.GL11._
    
    var isRunning = false; // is main loop running
    var (winWidth, winHeight)=(3000,3000); // window size
    var renderTime=0f;
    
    val timeLock = new TimeLock;
    var lastFPS = 0f; 
    
    /**
     * Initializes display and enters main loop
     */
    def main(Args:Array[String]) {
        try {
          initDisplay;
        } catch {
            case e:Exception => {
              print("Can't open display. "+e.getMessage);
              exit(1);
            }
        }

        // enter loop
        isRunning = true;
        mainLoop();
        
        // cleanup
        Display.destroy();
    }

    def initDisplay {
        var bestMode:DisplayMode = null;
        val modes:Array[DisplayMode] = Display.getAvailableDisplayModes;
        // Get best mode
        for(mode <- modes)
            if((mode.getWidth <= winWidth && mode.getHeight <= winHeight)
                &&(bestMode == null
                   ||(mode.getWidth >= bestMode.getWidth && mode.getHeight >= bestMode.getHeight
                      && mode.getBitsPerPixel >= bestMode.getBitsPerPixel)))
                bestMode = mode;
        
        Display.setDisplayMode(bestMode);
        winWidth = bestMode.getWidth;
        winHeight = bestMode.getHeight;
        
        println("Display: "+bestMode.getWidth+"x"+bestMode.getHeight+"@"+bestMode.getFrequency+"Hz, "+bestMode.getBitsPerPixel+"bit")
        
        try {
            // FSAA
            Display.create(new PixelFormat(8, 16, 0, 1));
        } catch {
            // No FSAA            
            case _ =>
                Display.create;
        }

        Display.setTitle("Angry Pigs");
        Display.setVSyncEnabled(true);
    }

    // used for frame independant movement
    var frameTime = currentTime;

    def decreaseDetail() = {
        Settings.graphics = (Settings.graphics-1);
        if(Settings.graphics==1 && Settings.maxdepth>5) Settings.maxdepth = 5;
        println("decreased graphic detail to "+Settings.graphics);
        models().foreach((model)=> {                        
            tasks += (()=> {
                model.compile();
                if(model.compileCache.size > 4) model.reset();
            });
        })
    }
    def increaseDetail() = {
        Settings.graphics = (Settings.graphics+1);
        Settings.maxdepth = (Settings.maxdepth+1);
        println("increased graphic detail to "+Settings.graphics);
        models().foreach((model)=> {                        
            tasks += (()=>{
                model.compile();
                if(model.compileCache.size > 4) model.reset();
            });
        })
    }
    
    /**
     * Main loop: renders and processes input events
     */
    def mainLoop() { 
        makeModels; // make generative models
        setupView;  // setup camera and lights
    
        // FPS counter
        var frameCounter = 0
        val second = 1000000000L
        val FPSseconds = 5
        var FPStimer = currentTime
        frameTime = currentTime
        while(isRunning) {
            processInput // process keyboard input
            
            resetView;      // clear view and reset transformations
            renderFrame;    // draw stuff
            Display.update; // update window contents and process input messages
            frameCounter += 1;

            if(currentTime-FPStimer > second*FPSseconds) {
                val FPS = frameCounter/FPSseconds.toFloat;
                // increase or decrease graphics detail
                if(lastFPS < 30 && FPS < 20 && Settings.graphics > 1 && tasks.length < 200) decreaseDetail();
                if(lastFPS > 50 && FPS > 50 && Settings.graphics < 2 && tasks.length < 200) increaseDetail();
            
                models().foreach((model)=>{
                    if(model.compileCache.size > 5) model.reset();
                })
                
                println("-------------------");
                println("FPS: "+FPS);
                println("Tasks: "+tasks.length);
                println("Render: "+(renderTimes/fullTimes.toDouble).toFloat)
                println("Physics: "+(physicsTimes/fullTimes.toDouble).toFloat)
                println("Worker: "+(workerTimes/fullTimes.toDouble).toFloat)
                println("-------------------");

                lastFPS = FPS
                frameCounter = 0;
                FPStimer = currentTime;
            }

            renderTime = (currentTime-frameTime)/frameIndepRatio;
            frameTime = currentTime;
        }
    }
    
    //models
    val cam = new Camera;
    var terrain = TerrainFactory();
    var pig = PigFactory()
    var catapult = CatapultFactory();
    var pigcatapultLink = new ModelLink(pig,catapult,new Vec3(0f,2.5f,0f));
    var trees = new ListBuffer[GeneratorModel];
    var futureTree:Future[GeneratorModel] = null;
    var dropBranches = new ListBuffer[GeneratorModel];
    var trails = new ListBuffer[TrailModel];

    def models():scala.collection.Traversable[DisplayModel] = {
        List(pig, catapult, terrain) ++ trees ++ dropBranches ++ trails
    }
    
    def makeModels {
        terrain.setPosition(-Settings.worldSize,-Settings.worldSize,-Settings.worldSize);
        terrain.setScale(Settings.worldSize*2, 5, Settings.worldSize*2);
        terrain.compile();
                
        pig.setPosition(0,-Settings.worldSize+10f,-Settings.worldSize/2+25);
        pig.compile();

        catapult.setPosition(0,-Settings.worldSize+2.5f,-Settings.worldSize/2+25);
        catapult.compile();
        
        pigcatapultLink.forgeLink();
        
        futureTree = future { TreeFactory() }
        futureTree.apply()
    }
    
    //@ Y is this not in some LWJGL lib, if it's really needed?
    def allocFloats(floatarray:Array[Float]):FloatBuffer = {
        val fb:FloatBuffer = ByteBuffer.allocateDirect(floatarray.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(floatarray).asInstanceOf[FloatBuffer].flip();
        return fb;
    }
    
    /**
    * Initial setup of projection of the scene onto screen, lights etc.
    */
    def setupView {
        glClearColor(0.3f,0.6f,0.8f,1f);

        glEnable(GL_DEPTH_TEST); // enable depth buffer (off by default)
        //glEnable(GL_CULL_FACE);  // enable culling of back sides of polygons
        //glCullFace(GL_BACK);
      
        // smooth shading - Gouraud
        glShadeModel(GL_SMOOTH);
        //glShadeModel(GL_FLAT);

        // lights
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);

        glLight(GL_LIGHT0, GL_AMBIENT, allocFloats(Array[Float](0.3f, 0.3f, 0.3f, 0.0f)));
        glLight(GL_LIGHT0, GL_DIFFUSE, allocFloats(Array[Float](0.7f, 0.7f, 0.7f, 0.0f)));
        glLightf(GL_LIGHT0, GL_LINEAR_ATTENUATION, 20f);
        glLight(GL_LIGHT0, GL_POSITION, allocFloats(Array[Float](0f, 0f, 10f, 0f)));
        glEnable(GL_COLOR_MATERIAL)
        glMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE, allocFloats(Array[Float](0.9f, 0.9f, 0.9f, 0f)));
        glColorMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE );
        
        glViewport(0,0, winWidth,winHeight); // mapping from normalized to window coordinates
       
        cam.setPerspective(50, winWidth/winHeight.toFloat, 1f, 600f);
        cam.setPosition(0,Settings.worldSize-5,-Settings.worldSize+5);
        cam.pos = pig.pos;
        cam.setRotation(0,0,0);
    }
  
    /**
    * Resets the view of current frame
    */
    def resetView {
        // clear color and depth buffer
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        //glMatrixMode(GL_PROJECTION);
        //glLoadIdentity;
        //glMatrixMode(GL_MODELVIEW);
        //glLoadIdentity;
    }
  
    var frameIndepRatio = (48000000f);
    var treeView = false;
    var pause = false

    def moveObj = if(treeView) trees(0) else { if(pigcatapultLink.isLinked) catapult else pig };
    
    var fullTimes = 0L
    var renderTimes = 0L
    var physicsTimes = 0L
    var workerTimes = 0L
    
    /**
    * Renders current frame
    */
    def renderFrame = fullTimes += time {
        workerTimes += time {///write tasks object
            def doTask() = {
                val task = tasks.head;
                task();
                tasks -= task;
                if(tasks.length==0) println("all tasks done");
            }
            // execute non-time-critical tasks... spread them out
            if(tasks.length>0 && !Settings.pigAir) {
                var cutoff = 50;
                if(pause) cutoff = 10;
                for(i <- 0 to tasks.length/cutoff) if(0.05f+(tasks.length-cutoff*i)/(cutoff.toFloat) > rand.nextFloat) 
                    doTask();
            }
       }
    
        if(!pause) {
            physicsTimes += time {
            // pig-catapult collision - hop on catapult :)
            if(!pigcatapultLink.isLinked && !Settings.pigAir) {
                var catapultBox = catapult.properties.get[BoundingBox]("box").clone;
                catapultBox.max.x += 2f;
                catapultBox.max.z += 2f;
                catapultBox.min.x -= 2f;
                catapultBox.min.z -= 2f;
                if(catapultBox.pointCollide(pig.pos, catapult.pos)) pigcatapultLink.forgeLink();
            }
            
            // move pig or catapult
            moveObj.vector.z -= 0.05f*moveObj.vector.z*renderTime;
            moveObj.vector.clamp(0,0,8);

            val moveVector = new Vec3(
                math.sin(moveObj.rot.y/(180f/math.Pi)).toFloat*moveObj.vector.z,
                moveObj.vector.y,
                math.cos(moveObj.rot.y/(180f/math.Pi)).toFloat*moveObj.vector.z
            )

            moveObj.pos.clamp(Settings.worldSize-2.5f);
            
            val mY = pig.pos.y;
            moveObj.pos += moveVector*renderTime;
            moveObj.pos.clamp(Settings.worldSize-2.5f);
            if(pig.pos.y==mY && Settings.pigAir) {
                Settings.pigAir = false;println("pig is on ground");
                val trailcount = 3///
                if(trails.length >= trailcount) 
                    trails = trails.drop(1);
            }
            def unrot(angle:Float, lim:Int=360):Float = { angle - ((math.floor(angle).toInt / lim)*lim) }
            pig.vector += Settings.gravity*renderTime
            pig.vector2 -= pig.vector2*renderTime*0.0175f;
            pig.rot += pig.vector2*renderTime
            pig.rot.x = unrot(pig.rot.x);
            var postpigrotx = unrot(pig.rot.x);
            val rotTreshold = 6f;
            
            if(Settings.pigAir) {
                if(trails.length==0) trails += new TrailModel(List(pig.pos));
                trails.last += moveObj.pos;
                trails.last.compile();
            } else if(!Settings.pigAir && (postpigrotx < rotTreshold || postpigrotx > 360f-rotTreshold)) {
                pig.vector2.x = 0;
                pig.rot.x = 0;
            } else {
                pig.vector2 -= pig.vector2*renderTime*0.01f;//trenje, lol
                if(pig.vector.x < 0.1) pig.vector.x = 0.1;
            }

            // collision detection
            for(tree <- trees) if(tree.visible) {
                var done = false;
                var collision = false;
                val branch = tree.data.asInstanceOf[Branch];
                def dropBranch(b:Branch):GeneratorModel = {                    
                    b.detach;
                    b.children.foreach((bc)=>{
                        if(bc.depth < Settings.maxdepth) dropBranch(bc);
                        bc.marked = true;
                    })
                    
                    if(rand.nextFloat > 0.4) b.properties += "hasLeaf" -> false;
                    
                    var drop = new GeneratorModel(()=>{b}, (data:Object)=>data.asInstanceOf[Branch].doAll(_.render));
                    drop.pos = tree.pos.clone;
                    drop.vector = new Vec3(
                        (math.sin(pig.rot.y/(180f/math.Pi)).toFloat*pig.vector.z/2)*(1+rand.nextFloat/6-rand.nextFloat/12) +rand.nextFloat/17-rand.nextFloat/17,
                        pig.vector.y/(5 + rand.nextFloat/4 - rand.nextFloat/4), 
                        (math.cos(pig.rot.y/(180f/math.Pi)).toFloat*pig.vector.z/2)*(1+rand.nextFloat/6-rand.nextFloat/12) +rand.nextFloat/17-rand.nextFloat/17
                    )
                    //drop.vector = new Vec3(0,0,0)
                    drop.compile();
                    dropBranches += drop
                    drop;
                }
                
                def getBox(m:DisplayModel):BoundingBox = {
                    var out = m.properties.get[BoundingBox]("box");
                    if(out==null) out = new BoundingBox(new Vec3);
                    out;
                }
                val moveBoxy = getBox(moveObj)
                moveObj.properties += "box" -> moveBoxy;
                def moveBox = moveBoxy offsetBy moveObj.pos

                branch.doWhile((b)=>{!done && !b.marked && b.depth <= Settings.maxdepth},
                    (b)=>{
                        val box = b.properties.get[BoundingBox]("box");
                        val canCollide:Boolean = box.pointCollide(pig.pos, tree.pos);
                        if(b.depth==1) {
                            if(canCollide) {
                                val basebox = (new BoundingBox(b.rootVec, b.destVec) offsetBy tree.pos)
                                basebox.min -= 2f;
                                basebox.max.x += 2f;
                                basebox.max.y -= 2f;
                                basebox.max.z += 2f;
                                if(basebox.boxCollide(moveBox)) {
                                    moveObj.vector.z = -moveObj.vector.z;
                                    if(math.abs(moveObj.vector.z) < 0.01f) moveObj.vector.z = 0.01f*math.abs(moveObj.vector.z)/moveObj.vector.z;
                                    var limit = 500;
                                    while(basebox.boxCollide(moveBox) && ({limit -= 1; limit} > 0)) {
                                        val moveVec = new Vec3(
                                            math.sin(moveObj.rot.y/(180f/math.Pi)).toFloat*moveObj.vector.z,
                                            0,
                                            math.cos(moveObj.rot.y/(180f/math.Pi)).toFloat*moveObj.vector.z
                                        )
                                        moveObj.pos += moveVec*renderTime;
                                    }
                                    moveObj.vector.z = moveObj.vector.z/2;
                                }
                            } else {
                                done = true;
                            }
                        } else //if(box.pointCollide(pig.pos, tree.pos) && 
                        if(Settings.pigAir && ((pig.pos-(box.min+tree.pos)).length < 4.25f || (pig.pos-(box.max+tree.pos)).length < 4.25f)) {
                            collision = true;
                            
                            pig.vector.y /= 2;
                            
                            var dropped = false;
                            
                            if(rand.nextFloat > 0.4) {
                                b.children.foreach((bc)=>{if(rand.nextFloat > 0.25) {dropped = true; dropBranch(bc)} });
                            } else {
                                dropped = true;
                                dropBranch(b);
                            }
                            
                            if(dropped) {
                                println("collision");
                                done = true;
                            } else {
                                collision = false;
                            }                            
                        }
                    }
                );
                if(collision) {
                    tree.compile();
                    tree.reset();

                    var depthSum = 0;
                    val sumLim = 2;
                    branch.doWhile((b)=>{depthSum <= sumLim},(b)=>{ depthSum += 1 });
                    if(depthSum <= sumLim) {// tree is dead
                        val drop = dropBranch(branch);
                        drop.vector.y = 2;
                        trees -= tree;
                        tree.free();
                    }
                }
            }

            // drop branches
            for(branch <- dropBranches) {
                branch.vector += (Settings.gravity)*renderTime;
                //branch.vector -= branch.vector*renderTime*0.05f;
                branch.pos += branch.vector*renderTime;
                if(branch.pos.y < -Settings.worldSize-50) {
                    dropBranches -= branch;
                    branch.free();
                    if(dropBranches.length==0) println("all broken branches removed");
                }
            }

            pigcatapultLink.applyLink;
            //campigLink.applyLink;
            }
        }
        
        //look at this pig... look!
        cam.lookAt(moveObj)
        val moveVec = new Vec3(
            math.sin(moveObj.rot.y/(180f/math.Pi)).toFloat*moveObj.vector.z,
            0,
            math.cos(moveObj.rot.y/(180f/math.Pi)).toFloat*moveObj.vector.z
        )
        if(moveObj eq pig) cam.angle = new Vec3(0,-1,50); else cam.angle = new Vec3(0,-7,60);
        val camMulti = 10f;
        //cam.pos = ((cam.pos*camMulti) + ((moveObj.pos - new Vec3(0f,0f,-50f))*renderTime))/(camMulti+renderTime);
        cam.pos = ((cam.pos*camMulti) + ((moveObj.pos - cam.angle)*renderTime))/(camMulti+renderTime);
        cam.vector -= cam.vector*renderTime*0.05f;
        cam.render
        
        if(futureTree==null) {
            if(trees.length < 5 && !Settings.pigAir && tasks.length < 500) futureTree = future { TreeFactory() }
        } else if(futureTree.isSet) {
            val presentTree = futureTree.apply();
            trees += presentTree;
            
            def growTree(lvl:Int, tree:GeneratorModel):Unit = {
                if(lvl <= Settings.maxdepth) {///move setting to tree!
                    val ex = Settings.maxdepth;
                    Settings.maxdepth = lvl;
                    tree.compile();
                    Settings.maxdepth = ex;
                }
            }
            
            growTree(2, presentTree)
            for(i <- 3 to Settings.maxdepth)
                tasks += (()=>{ growTree(i, presentTree) })
            
            futureTree = null;
            println("new tree added")
        }
        
        renderTimes += time {
            models().foreach(_.render)
        }
    }
    
    def processInput {
        import Keyboard._ // static imports.. fuckyeah
        
        if(Display.isCloseRequested || isKeyDown(KEY_ESCAPE)) {
            isRunning = false;
            return;
        }
                
        if(isKeyDown(KEY_T) && !timeLock.isLocked) {
            treeView = !treeView
            timeLock.lockIt(500);
        }
        if(isKeyDown(KEY_3)) { trees.foreach(_.regenerate()) } else
        if(isKeyDown(KEY_5) && !timeLock.isLocked) { 
            increaseDetail();
            timeLock.lockIt(300);
        } else
        if(isKeyDown(KEY_6) && !timeLock.isLocked && Settings.graphics > 1) { 
            decreaseDetail();
            timeLock.lockIt(300);
        } else
        if(isKeyDown(KEY_8) && !timeLock.isLocked) { pig.regenerate(); timeLock.lockIt(200); } else
        if(isKeyDown(KEY_9) && !timeLock.isLocked) { pig.compile(); timeLock.lockIt(200); }
        
        val keymove = 1.5f*renderTime;
        
        if(isKeyDown(KEY_W)) cam.pos.x+=keymove;
        if(isKeyDown(KEY_R)) cam.pos.x-=keymove;
        if(isKeyDown(KEY_S)) cam.pos.y+=keymove;
        if(isKeyDown(KEY_F)) cam.pos.y-=keymove;
        if(isKeyDown(KEY_X)) cam.pos.z+=keymove;
        if(isKeyDown(KEY_V)) cam.pos.z-=keymove;
/*        if(campigLink.isLinked) {
            if(isKeyDown(KEY_W)) campigLink.vector.x+=keymove;
            if(isKeyDown(KEY_R)) campigLink.vector.x-=keymove;
            if(isKeyDown(KEY_S)) campigLink.vector.y+=keymove;
            if(isKeyDown(KEY_F)) campigLink.vector.y-=keymove;
            if(isKeyDown(KEY_X)) campigLink.vector.z+=keymove;
            if(isKeyDown(KEY_V)) campigLink.vector.z-=keymove;
        } else {
        }*/

        if(isKeyDown(KEY_P)) {
            pause = true;
            println("paused")
        }
        if(isKeyDown(KEY_RSHIFT)) {
            pause = false;
            println("unpaused")
        }
        if(pause) return;

        if(isKeyDown(KEY_Z) && !timeLock.isLocked){
            terrain.visible = !terrain.visible
                
            timeLock.lockIt(100);            
        }

        if(isKeyDown(KEY_LEFT))  moveObj.rot.y+=keymove*3f;
        if(isKeyDown(KEY_RIGHT)) moveObj.rot.y-=keymove*3f;
        if(isKeyDown(KEY_UP))    moveObj.vector.z+=keymove/5f;
        if(isKeyDown(KEY_DOWN))  moveObj.vector.z-=keymove/5f;
        
        if(isKeyDown(KEY_SPACE) && (!Settings.pigAir)) {
            if(pigcatapultLink.isLinked) {
                pigcatapultLink.breakLink;
                println("pig-catapult Link Broken")
//                campigLink.breakLink
//                println("cam-pig Link Broken")
                pig.vector.y=3.7f;
                pig.vector.z=7.2f;
                //cam.vector = pig.vector / 3;
                pig.vector2 = new Vec3(0.5f+rand.nextFloat/3,0,0) * 50f;
            } else {
                pig.vector.y=2.7f;
                pig.vector.z=4f;
                if(isKeyDown(KEY_DOWN)) {
                    pig.vector.z = -pig.vector.z;
                    pig.vector2 = -new Vec3(0.5f+rand.nextFloat/3,0,0) * (80f*2.7f/3.7f);
                }
            }           
            
            Settings.pigAir = true;println("pig is in air");
            trails += new TrailModel(List(pig.pos))
        }
        if((isKeyDown(KEY_LCONTROL) || isKeyDown(KEY_RCONTROL))
        && !Settings.pigAir && !pigcatapultLink.isLinked) {
            pigcatapultLink.forgeLink;
        }
        
        if(isKeyDown(KEY_O)) {
            println("Cam: "+cam.toString);
            println("Pig: "+pig.toString);
            println("Pig-rot: "+pig.rot.toString);
        }
        if(isKeyDown(KEY_0)) {
            models().foreach(_.reset());
        }
    }
}



