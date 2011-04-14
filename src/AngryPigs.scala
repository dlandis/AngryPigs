import org.lwjgl._
import org.lwjgl.opengl._
import org.lwjgl.input._
import org.lwjgl.util.glu._
import scala.util._;

// TODO:
// search for @task

object AngryPigs {

    var isRunning = false; // is main loop running
    val (winWidth, winHeight)=(800,600); // window size
    val cam = new Camera;
    val rand = new Random;
    
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
        mainLoop;
        
        // cleanup
        Display.destroy;
    }

    def initDisplay {
        var bestMode:DisplayMode = null;
        val modes:Array[DisplayMode] = Display.getAvailableDisplayModes;
        // Get best mode
        for(mode <- modes)
            if((mode.getWidth == winWidth && mode.getHeight == winHeight && mode.getFrequency <= 85)
                &&(bestMode == null
                   ||(mode.getBitsPerPixel >= bestMode.getBitsPerPixel
                      && mode.getFrequency > bestMode.getFrequency)))
                bestMode = mode;
        
        Display.setDisplayMode(bestMode);
        // FSAA
        //Display.create(new PixelFormat(8, 8, 8, 4));
        // No FSAA
        Display.create;
        Display.setTitle("Angry Pigs");
    }
    /**
     * Main loop: renders and processes input events
     */
    def mainLoop { 
        //loadModels; // load models
        makeModels; // make generative models
        setupView;  // setup camera and lights
    
        // @that is one ugly FPS counter :)
        def now = System.nanoTime();
        var secondTimer = now;
        var frameCounter = 0;
        val E10 = 10000000000L;
        while(isRunning) {
            resetView;      // clear view and reset transformations
            renderFrame;    // draw stuff
            // @menda se da sproti/bolš gledat input
            processInput;   // process input events 
            Display.update; // update window contents and process input messages
            frameCounter += 1;

            //gl error
            val errCode = GL11.glGetError;
            if (errCode != GL11.GL_NO_ERROR) 
                println(opengl.Util.translateGLErrorString(errCode));

            if(now-secondTimer > E10) {
                secondTimer = now;
                // print fps
                println("FPS: "+frameCounter/10);
                frameCounter = 0;
            }
        }
    }
    
    //models
    var terrain:QuadPatch=null;
    var skybox:DisplayModel=null;
    var coordsys:DisplayModel=null;
    var pig:DisplayModel=null;
    //size of world
    val worldSize = 75;
    val gravity = new Vec3(0f,-0.5f,0f);
    
    // @would it pay-off to make model generation lazy and generate them on the fly?
    // @infinite terrain patches and stuff
    def makeModels {
        // terrain
        val detail=10;
        val height=0.1f;
        
        def getTerrainPoint(x:Int, y:Int):Vec3 = new Vec3(x/detail.toFloat,rand.nextFloat*height,y/detail.toFloat);
        val p = (for(i <- 0 to detail; j <- 0 to detail) yield getTerrainPoint(i,j)).toArray;        
        terrain = new QuadPatch(p, detail+1);
        terrain.setPosition(-worldSize,-worldSize,-worldSize);
        terrain.setScale(worldSize*2, 5, worldSize*2);
        
                // coordinate system
        coordsys = new DisplayModel(Unit=>{
            GL11.glBegin(GL11.GL_LINES);
                GL11.glColor3f(1,0,0);
                GL11.glVertex3f(0,0,0);
                GL11.glVertex3f(1,0,0);
                
                GL11.glColor3f(0,1,0);
                GL11.glVertex3f(0,0,0);
                GL11.glVertex3f(0,1,0);
                
                GL11.glColor3f(0,0,1);
                GL11.glVertex3f(0,0,0);
                GL11.glVertex3f(0,0,1);
            GL11.glEnd;//*/
        });
        coordsys.setScale(worldSize,worldSize,worldSize);

        // sky-box
        skybox = new DisplayModel(Unit=>{
            GL11.glBegin(GL11.GL_QUADS);
                // top
                GL11.glColor3f(0f,1f,0f); // green
                GL11.glVertex3f( 1f, 1f,-1f);
                GL11.glVertex3f(-1f, 1f,-1f);
                GL11.glVertex3f(-1f, 1f, 1f);
                GL11.glVertex3f( 1f, 1f, 1f);
                // bottom 
                /*GL11.glColor3f(1f,0.5f,0f);    // orange
                GL11.glVertex3f( 1f,-1f, 1f);
                GL11.glVertex3f(-1f,-1f, 1f);
                GL11.glVertex3f(-1f,-1f,-1f);
                GL11.glVertex3f( 1f,-1f,-1f);*/
                // Front
                GL11.glColor3f(1f,0f,0f); // red 
                GL11.glVertex3f( 1f, 1f, 1f);
                GL11.glVertex3f(-1f, 1f, 1f); 
                GL11.glVertex3f(-1f,-1f, 1f);
                GL11.glVertex3f( 1f,-1f, 1f);
                // back
                GL11.glColor3f(1f,1f,0f); // yellow
                GL11.glVertex3f( 1f,-1f,-1f);
                GL11.glVertex3f(-1f,-1f,-1f);
                GL11.glVertex3f(-1f, 1f,-1f);
                GL11.glVertex3f( 1f, 1f,-1f);
                // left
                GL11.glColor3f(0f,0f,1f); // blue
                GL11.glVertex3f(-1f, 1f, 1f);
                GL11.glVertex3f(-1f, 1f,-1f);
                GL11.glVertex3f(-1f,-1f,-1f);
                GL11.glVertex3f(-1f,-1f, 1f);
                // right
                GL11.glColor3f(1f,0f,1f); // violet
                GL11.glVertex3f( 1f, 1f,-1f);
                GL11.glVertex3f( 1f, 1f, 1f);
                GL11.glVertex3f( 1f,-1f, 1f);
                GL11.glVertex3f( 1f,-1f,-1f);
            GL11.glEnd;
        });
        skybox.setPosition(0,0,0);
        skybox.setScale(worldSize,worldSize,worldSize);//*/

        // pig
        pig = new DisplayModel(Unit=>{
            GL11.glColor3f(0.2f,0.7f,0.2f);
            val p = new Sphere();
            p.draw(2,10,10);
        });
        pig.setPosition(0,-worldSize+2.5f,-worldSize+25);
        //pig.setScale(worldSize/,worldSize,worldSize);//*/
    }

    /**
     * Initial setup of projection of the scene onto screen, lights etc.
     */
    def setupView {
        GL11.glEnable(GL11.GL_DEPTH_TEST); // enable depth buffer (off by default)
        //GL11.glEnable(GL11.GL_CULL_FACE);  // enable culling of back sides of polygons
      
        GL11.glViewport(0,0, winWidth,winHeight); // mapping from normalized to window coordinates
       
        //GL11.glHint(GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_NICEST);
        cam.setPerspective(45, winWidth/winHeight.toFloat, 1f, 500f);
        cam.setPosition(0,worldSize-5,-worldSize+5);
        cam.setRotation(0,180,0);
    }
  
    /**
    * Resets the view of current frame
    */
    def resetView {
        // clear color and depth buffer
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity;
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity;
    }
  
    /**
    * Renders current frame
    */
    def renderFrame {
        val toRender = List(
            cam,
            terrain,
            skybox,
            coordsys,
            pig
        )
        cam.pos.clamp(worldSize-5);

        pig.pos.applyVector(pig.vector);
        pig.vector.applyVector(gravity);
        pig.pos.clamp(worldSize-2.5f);

        toRender.map(_.render);
    }
    
    def processInput {
        if(Display.isCloseRequested || Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) isRunning = false;
        
        if(Keyboard.isKeyDown(Keyboard.KEY_Q)) cam.rot.x+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_E)) cam.rot.x-=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_A)) cam.rot.y+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_D)) cam.rot.y-=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_Y)) cam.rot.z+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_C)) cam.rot.z-=0.7f;

        if(Keyboard.isKeyDown(Keyboard.KEY_W)) cam.pos.x+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_R)) cam.pos.x-=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_S)) cam.pos.y+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_F)) cam.pos.y-=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_X)) cam.pos.z+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_V)) cam.pos.z-=0.7f;

        /*if(Keyboard.isKeyDown(Keyboard.KEY_T)) cam.scale.x+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_Z)) cam.scale.x-=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_G)) cam.scale.y+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_H)) cam.scale.y-=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_B)) cam.scale.z+=0.7f;
        if(Keyboard.isKeyDown(Keyboard.KEY_N)) cam.scale.z-=0.7f;*/

        if(Keyboard.isKeyDown(Keyboard.KEY_LEFT))  pig.vector.x+=0.2f;
        if(Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) pig.vector.x-=0.2f;
        if(Keyboard.isKeyDown(Keyboard.KEY_UP))    pig.vector.z+=0.2f;
        if(Keyboard.isKeyDown(Keyboard.KEY_DOWN))  pig.vector.z-=0.2f;
        if(Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            if (pig.vector.y <= 0) pig.vector.y=2f;
        }

        if(Keyboard.isKeyDown(Keyboard.KEY_P)) {
            println(cam.toString);
            println(pig.toString);
        }
    }    
}






