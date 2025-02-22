package me.isaiah.multiworld.command;

import java.util.HashMap;
import java.util.Optional;
import java.util.Random;

import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.impl.biome.BiomeSourceAccess;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.BuiltinBiomes;
import net.minecraft.world.biome.source.*;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import me.isaiah.multiworld.MultiworldMod;

import static me.isaiah.multiworld.MultiworldMod.text;
import static me.isaiah.multiworld.MultiworldMod.text_plain;
import net.minecraft.server.world.ServerWorld;

import java.io.File;
import me.isaiah.multiworld.config.*;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkGenerators;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

public class CreateCommand {
	
	public static HashMap<String, ChunkGenerator> customs;
	
	// TODO: expose API
	public static void registerCustomGenerator(Identifier id, ChunkGenerator gen) {
		customs.put(id.toString(), gen);
	}

	// Run Command
    public static int run(MinecraftServer mc, ServerPlayerEntity plr, String[] args) {
        if (args.length == 1 || args.length == 2) {
            plr.sendMessage(text_plain("Usage: /mv create <id> <env>"), false);
            return 0;
        }

        Random r = new Random();
        long seed = r.nextInt();
        
        String env = args[2];
        ChunkGenerator gen = get_chunk_gen(mc, env);
        Identifier dim = get_dim_id(env);

		//TODO: Ajout du nouveau ChunkGenerator
		Registry<ChunkGeneratorSettings> registry = mc.getRegistryManager().get(RegistryKeys.CHUNK_GENERATOR_SETTINGS);
		RegistryEntry<ChunkGeneratorSettings> registryEntry = registry.getEntry(registry.get(ChunkGeneratorSettings.OVERWORLD));
		MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.create();
		NoiseChunkGenerator noiseChunkGenerator = new NoiseChunkGenerator(biomeSource,registryEntry);

        if (null == dim) {
        	System.out.println("Null dimenstion ");
        	dim = Util.OVERWORLD_ID;
        }
        
        String arg1 = args[1];
        if (arg1.indexOf(':') == -1) {
        	arg1 = "multiworld:" + arg1;
        }
        
        ServerWorld world = MultiworldMod.create_world(arg1, dim, gen, Difficulty.NORMAL, seed);
		make_config(world, args[2], seed);

        plr.sendMessage(text("Created world with id: " + args[1], Formatting.GREEN), false);
        
        return 1;
    }

    /**
     * Return a {@link Identifier} representing the given vanilla environment,
     * or NULL if the passed argument is not NORMAL / NETHER / END.
     */
    public static Identifier get_dim_id(String env) {
    	if (env.contains("NORMAL")) {
			return Util.OVERWORLD_ID;
		}

		if (env.contains("NETHER")) {
			return Util.THE_NETHER_ID;
		}

		if (env.contains("END")) {
			return Util.THE_END_ID;
		}
		
		if (customs.containsKey(env)) {
			return MultiworldMod.new_id( env );
		}
		
		return null;
    }

    /**
     * Return a {@link ChunkGenerator} for the given vanilla environment,
     * or NULL if the passed argument is not NORMAL / NETHER / END.
     */
    public static ChunkGenerator get_chunk_gen(MinecraftServer mc, String env) {
		ChunkGenerator gen = MultiworldMod.get_world_creator().get_chunk_gen(mc, env);
		if (customs.containsKey(env)) {
			return customs.get(env);
		}
		return gen;
    } 
	
	public static void reinit_world_from_config(MinecraftServer mc, String id) {
		File config_dir = new File("config");
        config_dir.mkdirs();
		
		String[] spl = id.split(":");
        
        File cf = new File(config_dir, "multiworld"); 
        cf.mkdirs();

        File worlds = new File(cf, "worlds");
        worlds.mkdirs();

        File namespace = new File(worlds, spl[0]);
        namespace.mkdirs();

        File wc = new File(namespace, spl[1] + ".yml");
        FileConfiguration config;
        try {
			if (!wc.exists()) {
				wc.createNewFile();
			}
            config = new FileConfiguration(wc);
			String env = config.getString("environment");
			long seed = 0;
			
			try {
				seed = config.getLong("seed");
			} catch (Exception e) {
				seed = config.getInt("seed");
			}

			ChunkGenerator gen = get_chunk_gen(mc, env);
		    Identifier dim = get_dim_id(env);
		    
		    if (null == dim) {
		    	dim = Util.OVERWORLD_ID;
		    }
			
			Difficulty d = Difficulty.NORMAL;
			
			// Set saved Difficulty
			if (config.is_set("difficulty")) {
				String di = config.getString("difficulty");

				// String to Difficulty
				if (di.equalsIgnoreCase("EASY"))     { d = Difficulty.EASY; }
				if (di.equalsIgnoreCase("HARD"))     { d = Difficulty.HARD; }
				if (di.equalsIgnoreCase("NORMAL"))   { d = Difficulty.NORMAL; }
				if (di.equalsIgnoreCase("PEACEFUL")) { d = Difficulty.PEACEFUL; }
			}
			
			ServerWorld world = MultiworldMod.create_world(id, dim, gen, d, seed);

			if (GameruleCommand.keys.size() == 0) {
				GameruleCommand.setupServer(MultiworldMod.mc);
			}

			// Set Gamerules
			for (String name : GameruleCommand.keys.keySet()) {
				String key = "gamerule_" + name;
				
				if (config.is_set(key)) {
					
					Object o = config.getObject(key);
					
					// BoleanRule
					if (o instanceof Boolean) {
						o = ((Boolean) o) ? "true" : "false";
					}
					
					// IntRule
					if (o instanceof Integer) {
						o = String.valueOf((Integer) o);
					}
					
					GameruleCommand.set_gamerule_from_cfg(world, key, (String) o);
				}
			}
			
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public static void make_config(ServerWorld w, String dim, long seed) {
        File config_dir = new File("config");
        config_dir.mkdirs();
        
        File cf = new File(config_dir, "multiworld"); 
        cf.mkdirs();

        File worlds = new File(cf, "worlds");
        worlds.mkdirs();

        Identifier id = w.getRegistryKey().getValue();
        File namespace = new File(worlds, id.getNamespace());
        namespace.mkdirs();

        File wc = new File(namespace, id.getPath() + ".yml");
        FileConfiguration config;
        try {
			if (!wc.exists()) {
				wc.createNewFile();
			}
            config = new FileConfiguration(wc);
			config.set("namespace", id.getNamespace());
			config.set("path", id.getPath());
			config.set("environment", dim);
			config.set("seed", seed);
			config.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
