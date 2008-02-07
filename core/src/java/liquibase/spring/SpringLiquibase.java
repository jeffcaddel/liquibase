package liquibase.spring;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import liquibase.FileOpener;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.exception.JDBCException;
import liquibase.exception.LiquibaseException;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

/**
 * A Spring-ified wrapper for Liquibase.
 *
 * Example Configuration:
 * <p>
 * <p>
 * This Spring configuration example will cause liquibase to run
 * automatically when the Spring context is initialized. It will load
 * <code>db-changelog.xml</code> from the classpath and apply it against
 * <code>myDataSource</code>.
 * <p>
 *
 * <pre>
 * &lt;bean id=&quot;myLiquibase&quot;
 *          class=&quot;liquibase.spring.SpringLiquibase&quot;
 *          &gt;
 *
 *      &lt;property name=&quot;dataSource&quot; ref=&quot;myDataSource&quot; /&gt;
 *
 *      &lt;property name=&quot;changeLog&quot; value=&quot;classpath:db-changelog.xml&quot; /&gt;
 *
 *      &lt;!-- The following configuration options are optional --&gt;
 *
 *      &lt;property name=&quot;executeEnabled&quot; value=&quot;true&quot; /&gt;
 *
 *      &lt;!--
 *      If set to true, writeSqlFileEnabled will write the generated
 *      SQL to a file before executing it.
 *      --&gt;
 *      &lt;property name=&quot;writeSqlFileEnabled&quot; value=&quot;true&quot; /&gt;
 *
 *      &lt;!--
 *      sqlOutputDir specifies the directory into which the SQL file
 *      will be written, if so configured.
 *      --&gt;
 *      &lt;property name=&quot;sqlOutputDir&quot; value=&quot;c:\sql&quot; /&gt;
 *
 * &lt;/bean&gt;
 *
 * </pre>
 *
 * @author Rob Schoening
 */
public class SpringLiquibase implements InitializingBean, BeanNameAware, ResourceLoaderAware {
    public class SpringResourceOpener implements FileOpener {
        private String parentFile;

        public SpringResourceOpener(String parentFile) {
            this.parentFile = parentFile;
        }


        public InputStream getResourceAsStream(String file) throws IOException {
            Resource resource = getResource(file);

            return resource.getInputStream();
        }

        public Enumeration<URL> getResources(String packageName) throws IOException {
            Vector<URL> tmp = new Vector<URL>();

            tmp.add(getResource(packageName).getURL());

            return tmp.elements();
        }

        public Resource getResource(String file) {
            return getResourceLoader().getResource(adjustClasspath(file));
        }

        private String adjustClasspath(String file) {
            return isClasspathPrefixPresent(parentFile) && !isClasspathPrefixPresent(file)
                    ? ResourceLoader.CLASSPATH_URL_PREFIX + file
                    : file;
        }

        public boolean isClasspathPrefixPresent(String file) {
            return file.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX);
        }

        public ClassLoader toClassLoader() {
            return getResourceLoader().getClassLoader();
        }
    }

    private String beanName;

    private ResourceLoader resourceLoader;

    private DataSource dataSource;

    private boolean executeEnabled = true;

    private boolean writeSqlFileEnabled = true;

    private Logger log = Logger.getLogger(SpringLiquibase.class.getName());

    private String changeLog;

    private String contexts;

    private File sqlOutputDir;

    public SpringLiquibase() {
        super();
    }

    public String getDatabaseProductName() throws JDBCException {
        Connection connection = null;
        String name = "unknown";
        try {
            connection = getDataSource().getConnection();
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(dataSource.getConnection());
            name = database.getDatabaseProductName();
        } catch (SQLException e) {
            throw new JDBCException(e);
        } finally {
            if (connection != null) {
                try {
                    connection.rollback();
                    connection.close();
                } catch (Exception e) {
                    log.log(Level.WARNING, "problem closing connection", e);
                }
            }
        }
        return name;
    }

    /**
     * The DataSource that liquibase will use to perform the migration.
     *
     * @return
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * The DataSource that liquibase will use to perform the migration.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Determines whether liquibase will actually execute DDL statements.
     *
     * @param executeSql
     */
    public void setExecuteEnabled(boolean executeSql) {
        this.executeEnabled = executeSql;
    }

    /**
     * Determines whether liquibase will actually execute DDL statements.
     */
    public boolean isExecuteEnabled() {
        return executeEnabled;
    }

    /**
     * Returns a warning message that will be written out to the log.  It is here so that it
     * can be customized.
     *
     * @return
     */
    public String getExecuteDisabledWarningMessage() {
        return "\n\nYou have set "
                + getBeanName()
                + ".executeEnabled=false, but there are \n"
                + "database change sets that need to be run in order to bring the database up to date.\n"
                + "The application may not behave properly until these changes are run.\n\n";

    }

    /**
     * Returns the output directory into which a SQL file will be written *if*
     * writeSqlFileEnabled is set to true.
     *
     * @return
     */
    public File getSqlOutputDir() {
        return sqlOutputDir;
    }

    /**
     * Sets the output directory into which a SQL file will be written if
     * writeSqlFileEnabled is also set to true.
     *
     * @param sqlOutputDir
     */
    public void setSqlOutputDir(File sqlOutputDir) {
        this.sqlOutputDir = sqlOutputDir;
    }

    /**
     * Returns a Resource that is able to resolve to a file or classpath resource.
     *
     * @return
     */
    public String getChangeLog() {
        return changeLog;
    }

    /**
     * Sets a Spring Resource that is able to resolve to a file or classpath resource.
     * An example might be <code>classpath:db-changelog.xml</code>.
     */
    public void setChangeLog(String dataModel) {

        this.changeLog = dataModel;
    }

    public String getContexts() {
        return contexts;
    }

    public void setContexts(String contexts) {
        this.contexts = contexts;
    }

    /**
     * Executed automatically when the bean is initialized.
     */
    public void afterPropertiesSet() throws LiquibaseException {
        Connection c = null;
        try {
            c = getDataSource().getConnection();
            Liquibase liquibase = createLiquibase(c);

            if (isWriteSqlFileEnabled() && getSqlOutputDir() != null) {
                if (liquibase.listUnrunChangeSets(getContexts()).size() > 0) {
                    log.log(Level.WARNING, getExecuteDisabledWarningMessage());
                }
                writeSqlFile(liquibase);
            }

            // Now execute the DDL, if so configured
            if (isExecuteEnabled()) {
                executeSql(liquibase);
            }
        } catch (SQLException e) {
            throw new JDBCException(e);
        } finally {
            if (c != null) {
                try {
                    c.rollback();
                    c.close();
                } catch (SQLException e) {
                    ;
                }
            }
        }

    }

    /**
     * If there are any un-run changesets, this method will write them out to a file, if
     * so configured.
     *
     * @param liquibase
     *
     * @throws SQLException
     * @throws IOException
     * @throws LiquibaseException
     */
    protected void writeSqlFile(Liquibase liquibase) throws LiquibaseException {
        FileWriter fw = null;
        try {

            File ddlDir = getSqlOutputDir();
            ddlDir.mkdirs();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateString = sdf.format(new java.util.Date());

            String fileString = (getDatabaseProductName() + "-" + dateString + ".sql").replace(" ", "-").toLowerCase();
            File upgradeFile = new File(ddlDir, fileString);
            fw = new FileWriter(upgradeFile);

            liquibase.update(getContexts(), fw);
        } catch (IOException e) {
            throw new LiquibaseException(e);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "error closing fileWriter", e);
                }
            }
        }
    }

    private Liquibase createLiquibase(Connection c) throws JDBCException {
        return new Liquibase(getChangeLog(), new SpringResourceOpener(getChangeLog()), DatabaseFactory.getInstance().findCorrectDatabaseImplementation(c));
    }

    /**
     * This method will actually execute the changesets.
     *
     * @param liquibase
     *
     * @throws SQLException
     * @throws IOException
     */
    protected void executeSql(Liquibase liquibase) throws LiquibaseException {
        liquibase.update(getContexts());
    }

    /**
     * Boolean flag to determine whether generated SQL should be written to disk.
     *
     * @return
     */
    public boolean isWriteSqlFileEnabled() {
        return writeSqlFileEnabled;
    }

    /**
     * Boolean flag to determine whether generated SQL should be written to disk.
     */
    public void setWriteSqlFileEnabled(boolean writeSqlFile) {
        this.writeSqlFileEnabled = writeSqlFile;
    }


    /**
     * Spring sets this automatically to the instance's configured bean name.
     */
    public void setBeanName(String name) {
        this.beanName = name;
    }

    /**
     * Gets the Spring-name of this instance.
     *
     * @return
     */
    public String getBeanName() {
        return beanName;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }
}