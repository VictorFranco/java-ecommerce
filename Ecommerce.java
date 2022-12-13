package ecommerce;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.QueryParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.Response;

import java.sql.*;
import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import com.google.gson.*;

import java.util.ArrayList;

// la URL del servicio web es http://localhost:8080/Ecommerce/rest/ws
// donde:
//  "Ecommerce" es el dominio del servicio web (es decir, el nombre de archivo Ecommerce.war)
//  "rest" se define en la etiqueta <url-pattern> de <servlet-mapping> en el archivo WEB-INF\web.xml
//  "ws" se define en la siguiente anotacin @Path de la clase Ecommerce

@Path("ws")
public class Ecommerce {
  static DataSource pool = null;
  static {    
    try {
      Context ctx = new InitialContext();
      pool = (DataSource)ctx.lookup("java:comp/env/jdbc/datasoruce_ecommerce");
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  static Gson j = new GsonBuilder().registerTypeAdapter(byte[].class,new AdaptadorGsonBase64()).setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").create();

  @POST
  @Path("alta_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response altaArticulo(String json) throws Exception {
    ParamAltaArticulo p = (ParamAltaArticulo) j.fromJson(json,ParamAltaArticulo.class);
    Articulo articulo = p.articulo;
    Connection conexion = pool.getConnection();

    if (articulo.nombre == null || articulo.nombre.equals(""))
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar el nombre"))).build();

    if (articulo.descripcion == null || articulo.descripcion.equals(""))
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar la descripcion"))).build();

    try {
      conexion.setAutoCommit(false);    // iniciar transaccion

      PreparedStatement stmt_1 = conexion.prepareStatement("INSERT INTO articulos(id,nombre,descripcion,precio,cantidad,fotografia) VALUES (0,?,?,?,?,?)");
 
      try {
        stmt_1.setString(1,articulo.nombre);
        stmt_1.setString(2,articulo.descripcion);
        stmt_1.setFloat(3,articulo.precio);
        stmt_1.setInt(4,articulo.cantidad);
        stmt_1.setBytes(5,articulo.fotografia);
        stmt_1.executeUpdate();
      }
      finally {
        stmt_1.close();
      }
      conexion.commit();                // guardar cambios
    }
    catch (Exception e) {
      conexion.rollback();              // revertir cambios
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }
    finally {
      conexion.setAutoCommit(true);
      conexion.close();                 // cerrar conexion
    }
    return Response.ok().build();
  }

  @POST
  @Path("consulta_articulos")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response consultaArticulos(String json) throws Exception {
    ParamConsultaArticulo p = (ParamConsultaArticulo) j.fromJson(json,ParamConsultaArticulo.class);
    String keyword = p.keyword;
    Connection conexion= pool.getConnection();
    ArrayList<Articulo> articulos = new ArrayList<Articulo>();

    try {
      PreparedStatement stmt_1 = conexion.prepareStatement("SELECT nombre,descripcion,precio,cantidad,fotografia FROM articulos WHERE nombre LIKE ? OR descripcion LIKE ?");
      try {
        stmt_1.setString(1,'%'+keyword+'%');
        stmt_1.setString(2,'%'+keyword+'%');

        ResultSet rs = stmt_1.executeQuery();
        try {
          while (rs.next()) {
            Articulo r = new Articulo();
            r.nombre = rs.getString(1);
            r.descripcion = rs.getString(2);
            r.precio = rs.getFloat(3);
            r.cantidad = rs.getInt(4);
            r.fotografia = rs.getBytes(5);
            articulos.add(r);
          }
        }
        finally {
          rs.close();
        }
      }
      finally {
        stmt_1.close();
      }
    }
    catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }
    finally {
      conexion.close();
    }
    return Response.ok().entity(j.toJson(articulos)).build();
  }

  @POST
  @Path("agregar_a_carrito")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response agregarACarrito(String json) throws Exception {
    ParamAgregarACarrito p = (ParamAgregarACarrito) j.fromJson(json,ParamAgregarACarrito.class);
    String nombre = p.nombre;
    int cantidad = p.cantidad;
    Connection conexion = pool.getConnection();
    int cantidad_actual = 0;
    int id = 0;

    try {
      PreparedStatement stmt_1 = conexion.prepareStatement("SELECT id,cantidad FROM articulos WHERE nombre=?");
      try {
        stmt_1.setString(1,nombre);

        ResultSet rs = stmt_1.executeQuery();
        try {
          if (rs.next()) {
            id = rs.getInt(1);
            cantidad_actual = rs.getInt(2);
          }
        }
        finally {
          rs.close();
        }
      }
      finally {
        stmt_1.close();
      }
    }
    catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }

    if (cantidad > cantidad_actual) {
      conexion.close();
      return Response.status(400).entity(j.toJson(new Error("Hay " + cantidad_actual + " productos disponibles"))).build();
    }

    cantidad_actual -= cantidad;

    try {
      conexion.setAutoCommit(false);  // iniciar transaccion

      PreparedStatement stmt_1 = conexion.prepareStatement("UPDATE articulos SET cantidad=? WHERE nombre=?");
      try {
        stmt_1.setInt(1,cantidad_actual);
        stmt_1.setString(2,nombre);
        stmt_1.executeUpdate();
      }
      finally {
        stmt_1.close();
      }

      PreparedStatement stmt_2 = conexion.prepareStatement("INSERT INTO carrito_compra(id,cantidad,id_articulo) VALUES (0,?,?)");
      try {
        stmt_2.setInt(1,cantidad);
        stmt_2.setInt(2,id);
        stmt_2.executeUpdate();
      }
      finally {
        stmt_2.close();
      }

      conexion.commit();              // guardar cambios
    }
    catch (Exception e) {
      conexion.rollback();            // revertir cambios
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }
    finally {
      conexion.setAutoCommit(true);   // cerrar conexion
      conexion.close();
    }
    return Response.ok().build();
  }

  @POST
  @Path("mostrar_carrito")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response mostrarCarrito(String json) throws Exception {
    Connection conexion= pool.getConnection();
    ArrayList<Articulo> articulos = new ArrayList<Articulo>();

    try {
      PreparedStatement stmt_1 = conexion.prepareStatement("SELECT c.id, a.nombre, a.descripcion, a.precio, c.cantidad, a.fotografia FROM carrito_compra c INNER JOIN articulos a ON c.id_articulo=a.id;");
      try {
        ResultSet rs = stmt_1.executeQuery();
        try {
          while (rs.next()) {
            Articulo r = new Articulo();
            r.id = rs.getInt(1);
            r.nombre = rs.getString(2);
            r.descripcion = rs.getString(3);
            r.precio = rs.getFloat(4);
            r.cantidad = rs.getInt(5);
            r.fotografia = rs.getBytes(6);
            articulos.add(r);
          }
        }
        finally {
          rs.close();
        }
      }
      finally {
        stmt_1.close();
      }
    }
    catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }
    finally {
      conexion.close();
    }
    return Response.ok().entity(j.toJson(articulos)).build();
  }

  @POST
  @Path("borrar_de_carrito")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response borrarDeCarrito(String json) throws Exception {
    Connection conexion= pool.getConnection();
    ParamBorrarDeCarrito p = (ParamBorrarDeCarrito) j.fromJson(json,ParamBorrarDeCarrito.class);
    int id_carrito = p.id;
    int cantidad = 0;
    int id = 0;

    try {
      PreparedStatement stmt_1 = conexion.prepareStatement("SELECT c.id_articulo, c.cantidad, a.cantidad FROM carrito_compra c INNER JOIN articulos a ON c.id_articulo=a.id WHERE c.id=?");
      try {
        stmt_1.setInt(1,id_carrito);
        ResultSet rs = stmt_1.executeQuery();
        try {
          if (rs.next()) {
            id = rs.getInt(1);
            cantidad = rs.getInt(2) + rs.getInt(3);
          }
        }
        finally {
          rs.close();
        }
      }
      finally {
        stmt_1.close();
      }
    }
    catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }

    try {
      conexion.setAutoCommit(false);  // iniciar transaccion

      PreparedStatement stmt_1 = conexion.prepareStatement("UPDATE articulos SET cantidad=? WHERE id=?");
      try {
        stmt_1.setInt(1,cantidad);
        stmt_1.setInt(2,id);
        stmt_1.executeUpdate();
      }
      finally {
        stmt_1.close();
      }

      PreparedStatement stmt_2 = conexion.prepareStatement("DELETE FROM carrito_compra WHERE id=?");
      try {
        stmt_2.setInt(1,id_carrito);
        stmt_2.executeUpdate();
      }
      finally {
        stmt_2.close();
      }

      conexion.commit();              // guardar cambios
    }
    catch (Exception e) {
      conexion.rollback();            // revertir cambios
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }
    finally {
      conexion.setAutoCommit(true);
      conexion.close();               // cerrar conexion
    }
    return Response.ok().build();
  }

  @POST
  @Path("borrar_carrito")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response borrarCarrito(String json) throws Exception {
    Connection conexion= pool.getConnection();
    int cantidad = 0;
    int id = 0;

    try {
      PreparedStatement stmt_1 = conexion.prepareStatement("SELECT c.id_articulo, c.cantidad, a.cantidad FROM carrito_compra c INNER JOIN articulos a ON c.id_articulo=a.id");
      try {
        ResultSet rs = stmt_1.executeQuery();
        try {
          while (rs.next()) {
            id = rs.getInt(1);
            cantidad = rs.getInt(2) + rs.getInt(3);
            try {
              conexion.setAutoCommit(false);  // transaccion iniciada

              PreparedStatement stmt_2 = conexion.prepareStatement("UPDATE articulos SET cantidad=? WHERE id=?");
              try {
                stmt_2.setInt(1,cantidad);
                stmt_2.setInt(2,id);
                stmt_2.executeUpdate();
              }
              finally {
                stmt_2.close();
              }

              conexion.commit();              // guardar cambios
            }
            catch (Exception e) {
              conexion.rollback();          // revertir cambios
              conexion.setAutoCommit(true);
              conexion.close();             // cerrar conexion
              return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
            }
          }
        }
        finally {
          rs.close();
        }
      }
      finally {
        stmt_1.close();
      }
    }
    catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }

    try {
      conexion.setAutoCommit(false);  // transaccion iniciada

      PreparedStatement stmt_1 = conexion.prepareStatement("DELETE FROM carrito_compra");
      try {
        stmt_1.executeUpdate();
      }
      finally {
        stmt_1.close();
      }

      conexion.commit();              // guardar cambios
    }
    catch (Exception e) {
      conexion.rollback();            // revertir cambios
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    }
    finally {
      conexion.setAutoCommit(true);
      conexion.close();               // cerrar conexion
    }
    return Response.ok().build();
  }
}
