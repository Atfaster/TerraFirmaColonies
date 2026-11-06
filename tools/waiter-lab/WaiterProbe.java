package lab;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkCook;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import net.ckeeze.terrafirmacolonies.api.TFCFoodUtils;
import net.dries007.tfc.common.capabilities.food.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Disposable real-Forge method integration tests, NOT a natural AI/pathfinding playthrough. */
@Mod("waiter_probe")
public class WaiterProbe {
    private net.minecraft.server.MinecraftServer server;
    private int ticks;
    private final List<Map<String,Object>> tests = new ArrayList<>();
    private IColony colony;
    private BuildingCook restaurant;
    private IBuilding home;
    private EntityCitizen guest, waiter;
    private ICitizenData guestData, waiterData;
    private IItemHandlerModifiable stock;
    private RestaurantMenuModule menu;
    private EntityAIWorkCook ai;
    private final BlockPos hut = new BlockPos(8,120,8);
    public WaiterProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void started(ServerStartedEvent event) { server=event.getServer();server.m_129783_().m_8602_(0,0,true); }
    private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if(server!=null && event.phase==net.minecraftforge.event.TickEvent.Phase.END && ++ticks==60) run();
    }
    static Field field(Class<?> c,String name) throws Exception {
        for(;c!=null;c=c.getSuperclass()) try {var f=c.getDeclaredField(name);f.setAccessible(true);return f;} catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    static void set(Object o,String name,Object value) throws Exception {field(o.getClass(),name).set(o,value);}
    static Object call(Object o,String name) throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()) try {var m=c.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);} catch(NoSuchMethodException ignored) {}
        throw new NoSuchMethodException(name);
    }
    void record(String name,Object... values) {
        var r=new LinkedHashMap<String,Object>();r.put("case",name);
        for(int i=0;i<values.length;i+=2)r.put(values[i].toString(),values[i+1]);tests.add(r);
    }
    static ItemStack item(String id) {return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id))));}
    IBuilding building(String id,BlockPos p) {
        return IMinecoloniesAPI.getInstance().getBuildingRegistry().getValue(new ResourceLocation("minecolonies",id)).produceBuilding(p,colony);
    }
    EntityCitizen citizen() throws Exception {
        var data=colony.getCitizenManager().createAndRegisterCivilianData();
        var e=(EntityCitizen)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecolonies:citizen")).m_20615_(server.m_129783_());
        set(e,"citizenData",data);e.setCitizenId(data.getId());e.getCitizenColonyHandler().setColonyId(colony.getID());
        set(e.getCitizenColonyHandler(),"colony",colony);set(e.getCitizenColonyHandler(),"registered",true);
        set(data,"entity",new java.lang.ref.WeakReference<>(e));
        e.m_6034_(8,121,8);server.m_129783_().m_7967_(e);return e;
    }
    void fixture() throws Exception {
        var world=server.m_129783_();world.m_8602_(0,0,true);
        var cap=((net.minecraftforge.common.capabilities.ICapabilityProvider)world).getCapability(MineColonies.COLONY_MANAGER_CAP).resolve().orElseThrow();
        colony=cap.createColony(world,hut);
        var chunk=world.m_46745_(hut);
        ((net.minecraftforge.common.capabilities.ICapabilityProvider)chunk).getCapability(IColony.CLOSE_COLONY_CAP).resolve().orElseThrow().setOwningColony(colony.getID(),chunk);
        restaurant=(BuildingCook)building("cook",hut);home=building("residence",new BlockPos(24,120,8));
        set(colony.getServerBuildingManager(),"buildings",ImmutableMap.of(hut,restaurant,home.getPosition(),home));
        restaurant.setBuildingLevel(1);home.setBuildingLevel(1);home.setCorners(new BlockPos(20,119,2),new BlockPos(28,125,14));
        restaurant.setCorners(new BlockPos(2,119,2),new BlockPos(14,125,14));set(restaurant,"isBuilt",true);
        for(int x=2;x<=14;x++)for(int z=2;z<=14;z++) world.m_7731_(new BlockPos(x,119,z),ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).m_49966_(),3);
        world.m_7731_(hut,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecolonies:blockhutcook")).m_49966_(),3);
        var tile=(com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding)world.m_7702_(hut);
        tile.setColony(colony);restaurant.setTileEntity(tile);
        stock=(IItemHandlerModifiable)tile.getCapability(ForgeCapabilities.ITEM_HANDLER,null).resolve().orElseThrow();
        menu=restaurant.getModule(BuildingModules.RESTAURANT_MENU);
        guest=citizen();guestData=guest.getCitizenData();set(guestData,"homeBuilding",home);guestData.setSaturation(0);
        waiter=citizen();waiterData=waiter.getCitizenData();set(waiterData,"homeBuilding",home);
        var job=new JobCook(waiterData);job.setRegistryEntry(IMinecoloniesAPI.getInstance().getJobRegistry().getValue(new ResourceLocation("minecolonies:cook")));
        set(job,"workBuilding",restaurant);set(waiterData,"job",job);
        var module=restaurant.getFirstModuleOccurance(WorkerBuildingModule.class);
        @SuppressWarnings("unchecked") var assigned=(List<ICitizenData>)field(module.getClass(),"assignedCitizen").get(module);
        assigned.add(waiterData);set(job,"workModule",module);
        ai=new EntityAIWorkCook(job);
        set(ai,"building",restaurant);
        record("fixture","containers",restaurant.getContainers().toString(),"furnaces",restaurant.getFirstModuleOccurance(FurnaceUserModule.class).getFurnaces().toString(),"scope","real runtime objects; direct method calls; no natural navigation");
    }
    static ItemStack meal(float grain,float veg,float protein,float dairy) {
        var s=item("tfc:food/barley_bread_sandwich");
        ((FoodHandler.Dynamic)FoodCapability.get(s)).setFood(new FoodData(4,0.5f,5.6f,grain,0,veg,protein,dairy,4.5f));
        FoodCapability.setStackNonDecaying(s);return s;
    }
    void put(ItemStack s,boolean selected) {
        for(int i=0;i<stock.getSlots();i++)stock.setStackInSlot(i,ItemStack.f_41583_);
        stock.setStackInSlot(0,s.m_41777_());menu.getMenu().clear();
        if(selected)menu.getMenu().add(new ItemStorage(new ItemStack(s.m_41720_())));
        try {call(restaurant.getTileEntity(),"updateContent");}catch(Exception ex){throw new RuntimeException(ex);}
    }
    void cases() throws Exception {
        var food=meal(1.5f,1.6f,2,2.4f); food.m_41764_(64);
        home.setBuildingLevel(5);set(guestData,"homeBuilding",home);
        boolean fixed=Boolean.getBoolean("feeding.fixed");
        check(com.minecolonies.api.util.WorldUtil.isBlockLoaded(server.m_129783_(),hut),"fixture chunk loaded");
        check(com.minecolonies.api.util.WorldUtil.getEntitiesWithinBuilding(server.m_129783_(),EntityCitizen.class,restaurant,null).size()==2,"fixture citizens visible");
        for(int level=1;level<=5;level++)for(boolean fuel:new boolean[]{false,true})for(String location:List.of("waiter","hut","none","excluded_menu")) {
            restaurant.setBuildingLevel(level);
            var fuels=restaurant.getModuleMatching(ItemListModule.class,v->v.getId().equals("fuel"));
            fuels.clearItems();if(fuel)fuels.addItem(new ItemStorage(item("minecraft:charcoal")));
            for(var citizen:List.of(waiter,guest))for(int i=0;i<citizen.getInventoryCitizen().getSlots();i++)citizen.getInventoryCitizen().setStackInSlot(i,ItemStack.f_41583_);
            ((Queue<?>)field(ai.getClass(),"citizenToServe").get(ai)).clear();
            put(location.equals("hut")?food:ItemStack.f_41583_,false);
            if(!location.equals("excluded_menu"))menu.getMenu().add(new ItemStorage(new ItemStack(food.m_41720_())));
            if(location.equals("waiter")||location.equals("excluded_menu"))waiter.getInventoryCitizen().setStackInSlot(0,food.m_41777_());
            guestData.setSaturation(0);guestData.setJustAte(false);guestData.setWorking(false);
            String before=ai.getState().toString(), state=ai.startWorking().toString();
            String expected=!fixed?before:location.equals("waiter")?"COOK_SERVE_FOOD_TO_CITIZEN":location.equals("hut")?"GATHERING_REQUIRED_MATERIALS":"START_WORKING";
            check(state.equals(expected),"dispatch "+level+"/"+fuel+"/"+location+": "+state+" expected "+expected);
            int received=0;
            if(state.equals("COOK_SERVE_FOOD_TO_CITIZEN")) {
                call(ai,"serveFoodToCitizen");
                received=guest.getInventoryCitizen().getStackInSlot(0).m_41613_();
                check(received>0 && received+waiter.getInventoryCitizen().getStackInSlot(0).m_41613_()==64,"real inventory transfer, no duplication");
            }
            record("ready_food","level",level,"fuel_selected",fuel,"location",location,"state",state,"guest_received",received,"furnaces",restaurant.getFirstModuleOccurance(FurnaceUserModule.class).getFurnaces().size());
        }
    }
    static void check(boolean condition,String description) {if(!condition)throw new AssertionError(description);}
    static void write(String name,Object value)throws Exception {Files.createDirectories(Path.of("../evidence"));Files.writeString(Path.of("../evidence",name),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(value));}
    void run() {
        try {
            if(!Boolean.getBoolean("feeding.lab"))throw new IllegalStateException("Disposable lab required");
            fixture();cases();write("cases.json",tests);System.out.println("WAITER_PROBE COMPLETE");
        } catch(Throwable ex){ex.printStackTrace();try{write("cases-partial.json",tests);}catch(Exception ignored){}System.out.println("WAITER_PROBE FAILED "+ex);}
    }
}
