package de.gnmyt.mcdash.api;

import de.gnmyt.mcdash.MCDashWrapper;
import de.gnmyt.mcdash.entities.Server;
import de.gnmyt.mcdash.entities.ServerConfiguration;
import de.gnmyt.mcdash.entities.ServerStatus;

import java.io.File;
import java.util.ArrayList;

public class ServerManager {

    private static final Logger LOG = new Logger(ServerManager.class);
    private static final ServerVersionManager versionManager = new ServerVersionManager();
    private final ArrayList<Server> servers = new ArrayList<>();

    private final File serverFolder;

    /**
     * Basic constructor of the {@link ServerManager}
     */
    public ServerManager() {
        this.serverFolder = MCDashWrapper.getDataSource("servers");
    }

    /**
     * Initializes the server manager
     */
    public void initialize() {
        if (!serverFolder.exists() && !serverFolder.mkdirs()) {
            LOG.error("An error occurred while creating the server folder");
        }

        refreshServers();
    }

    /**
     * Starts all servers
     */
    public void startServers() {
        LOG.info("Starting all servers...");

        servers.forEach(server -> {
            if (server.getStatus() == ServerStatus.ONLINE) return;
            startServer(server);
        });
    }

    /**
     * Stops all servers
     */
    public void stopServers() {
        LOG.info("Stopping all servers...");

        servers.forEach(server -> {
            if (server.getStatus() == ServerStatus.OFFLINE) return;
            stopServer(server);
        });
    }

    /**
     * Refreshes the servers
     */
    public void refreshServers() {
        servers.stream().filter(server -> server.getStatus() == ServerStatus.OFFLINE).forEach(servers::remove);

        for (File file : serverFolder.listFiles()) {
            if (file.isDirectory() && new File(file, "mcdash.json").exists()) {
                if (getServer(file.getName()) != null) continue;

                servers.add(new Server(file.getName(), ServerStatus.OFFLINE, new File(file, "mcdash.json")));
            }
        }
    }

    /**
     * Gets a server by its name.
     * <p>
     * If the server does not exist, it will return null
     * </p>
     *
     * @param name The name of the server
     * @return the server with the given name
     */
    public Server getServer(String name) {
        return servers.stream().filter(server -> server.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Starts a server
     * @param server The server you want to start
     */
    public void startServer(Server server) {
        if (server.getStatus() == ServerStatus.ONLINE) return;
        LOG.info("Starting server {}", server.getName());

        ServerConfiguration config = server.getConfiguration();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", "-Xmx" + config.getMemory() + "M",
                    "-Xms" + config.getMemory() + "M", "-Dcom.mojang.eula.agree=true", // "-Djava.awt.headless=true",
                    versionManager.getPath(config.getType(), config.getVersion())//, "nogui"
            );
            processBuilder.directory(new File(serverFolder, server.getName()));
            Process process = processBuilder.start();

            server.keepAlive();

            server.setStatus(ServerStatus.ONLINE);
            server.setProcess(process);
        } catch (Exception e) {
            LOG.error("An error occurred while starting the server", e);
        }

        LOG.info("Server {} has been started", server.getName());
    }

    /**
     * Stops a server
     * @param server The server you want to stop
     */
    public void stopServer(Server server) {
        if (server.getStatus() == ServerStatus.OFFLINE) return;

        LOG.info("Stopping server {}", server.getName());

        try {
            server.getProcess().getOutputStream().write("stop\n".getBytes());

            for (int i = 0; i < 10; i++) {
                if (server.getProcess().isAlive()) {
                    Thread.sleep(1000);
                } else {
                    break;
                }
            }

            server.getProcess().destroy();
            server.setStatus(ServerStatus.OFFLINE);
            server.setProcess(null);
        } catch (Exception e) {
            LOG.error("An error occurred while stopping the server", e);
        }

        LOG.info("Server {} has been stopped", server.getName());
    }

}
