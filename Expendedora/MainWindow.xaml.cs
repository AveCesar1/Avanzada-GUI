using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Data.SQLite; // Cambiar este using
using System.Linq;

namespace MyWpfApp
{
    public partial class MainWindow : Window
    {
        private DatabaseManager dbManager;
        private Producto productoSeleccionado;
        private decimal totalIngresado = 0;
        private Dictionary<decimal, int> dineroIngresado = new Dictionary<decimal, int>();

        // Denominaciones disponibles
        private readonly decimal[] denominaciones = { 0.50m, 1, 2, 5, 20, 50, 100, 200 };

        public MainWindow()
        {
            InitializeComponent();
            dbManager = new DatabaseManager();
            InicializarMaquina();
            CargarInterfaz();
        }

        private void InicializarMaquina()
        {
            dbManager.InicializarBaseDeDatos();
            dineroIngresado = denominaciones.ToDictionary(d => d, d => 0);
        }

        private void CargarInterfaz()
        {
            // Cargar productos
            var productos = dbManager.ObtenerProductos();
            ProductosContainer.ItemsSource = productos.Select(p => new
            {
                Producto = p,
                DisplayText = $"{p.Nombre}\n${p.Precio} - Stock: {p.Cantidad}"
            });

            // Cargar botones de dinero
            MoneyButtonsPanel.Children.Clear();
            foreach (var denom in denominaciones)
            {
                var button = new Button
                {
                    Content = $"${denom}",
                    Tag = denom,
                    Style = (Style)FindResource("MoneyButton")
                };
                button.Click += MoneyButton_Click;
                MoneyButtonsPanel.Children.Add(button);
            }

            ActualizarEstado();
        }

        private void ProductButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button button && button.Tag != null)
            {
                dynamic item = button.Tag;
                productoSeleccionado = item.Producto;

                txtProductoSeleccionado.Text = $"Producto: {productoSeleccionado.Nombre}";
                txtPrecioProducto.Text = $"Precio: ${productoSeleccionado.Precio}";
                txtMensaje.Text = $"Seleccionado: {productoSeleccionado.Nombre}. Ingrese el pago.";
                
                VerificarCambioDisponible();
            }
        }

        private void MoneyButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button button && button.Tag is decimal denominacion)
            {
                dineroIngresado[denominacion]++;
                totalIngresado += denominacion;
                txtTotalIngresado.Text = totalIngresado.ToString("F2");
                
                if (productoSeleccionado != null)
                {
                    VerificarCambioDisponible();
                }
            }
        }

        private void VerificarCambioDisponible()
        {
            if (productoSeleccionado == null) return;

            decimal cambioNecesario = totalIngresado - productoSeleccionado.Precio;
            
            if (cambioNecesario > 0)
            {
                var cambioDisponible = dbManager.CalcularCambioDisponible(cambioNecesario);
                if (!cambioDisponible.esPosible)
                {
                    txtMensaje.Text = "⚠️ No tengo cambio suficiente. Solo puedo aceptar cantidad exacta.";
                    txtMensaje.Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.Red);
                }
                else
                {
                    txtMensaje.Text = $"Puede pagar. Cambio: ${cambioNecesario:F2}";
                    txtMensaje.Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.Green);
                }
                txtCambio.Text = $"Cambio: ${cambioNecesario:F2}";
            }
            else
            {
                txtCambio.Text = "Cambio: $0.00";
                txtMensaje.Text = "Ingrese más dinero o realice la compra";
                txtMensaje.Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.Black);
            }
        }

        private void BtnComprar_Click(object sender, RoutedEventArgs e)
        {
            if (productoSeleccionado == null)
            {
                MessageBox.Show("Seleccione un producto primero.", "Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            if (totalIngresado < productoSeleccionado.Precio)
            {
                MessageBox.Show($"Dinero insuficiente. Necesita: ${productoSeleccionado.Precio - totalIngresado:F2} más.", 
                    "Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            decimal cambioNecesario = totalIngresado - productoSeleccionado.Precio;
            
            // Verificar si hay cambio disponible
            if (cambioNecesario > 0)
            {
                var resultadoCambio = dbManager.CalcularCambioDisponible(cambioNecesario);
                if (!resultadoCambio.esPosible)
                {
                    MessageBox.Show("No tengo cambio suficiente para esta transacción. Por favor, use cantidad exacta.", 
                        "Sin Cambio", MessageBoxButton.OK, MessageBoxImage.Warning);
                    return;
                }
            }

            // Realizar la compra
            var resultado = dbManager.RealizarCompra(productoSeleccionado.Id, dineroIngresado, cambioNecesario);
            
            if (resultado.exitoso)
            {
                MessageBox.Show($"✅ Compra exitosa!\nProducto: {productoSeleccionado.Nombre}\nCambio: ${cambioNecesario:F2}", 
                    "Éxito", MessageBoxButton.OK, MessageBoxImage.Information);
                
                // Limpiar
                BtnCancelar_Click(sender, e);
                CargarInterfaz();
            }
            else
            {
                MessageBox.Show($"Error: {resultado.mensaje}", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnCancelar_Click(object sender, RoutedEventArgs e)
        {
            productoSeleccionado = null;
            totalIngresado = 0;
            dineroIngresado = denominaciones.ToDictionary(d => d, d => 0);
            
            txtProductoSeleccionado.Text = "Producto: Ninguno";
            txtPrecioProducto.Text = "Precio: $0.00";
            txtCambio.Text = "Cambio: $0.00";
            txtTotalIngresado.Text = "0.00";
            txtMensaje.Text = "Seleccione un producto e ingrese el pago";
            txtMensaje.Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.Black);
        }

        private void BtnRecargar_Click(object sender, RoutedEventArgs e)
        {
            var ventanaRecarga = new VentanaRecarga(dbManager);
            ventanaRecarga.Owner = this;
            ventanaRecarga.ShowDialog();
            CargarInterfaz();
        }

        private void BtnVerEstado_Click(object sender, RoutedEventArgs e)
        {
            var estado = dbManager.ObtenerEstadoMaquina();
            string mensaje = "ESTADO DE LA MÁQUINA:\n\n";
            
            mensaje += "PRODUCTOS:\n";
            foreach (var prod in estado.Productos)
            {
                mensaje += $"- {prod.Nombre}: {prod.Cantidad} unidades\n";
            }
            
            mensaje += "\nDINERO DISPONIBLE:\n";
            foreach (var dinero in estado.Dinero)
            {
                mensaje += $"- ${dinero.Denominacion}: {dinero.Cantidad} unidades\n";
            }

            MessageBox.Show(mensaje, "Estado de la Máquina", MessageBoxButton.OK, MessageBoxImage.Information);
        }

        private void ActualizarEstado()
        {
            var estado = dbManager.ObtenerEstadoMaquina();
            int productosAgotados = estado.Productos.Count(p => p.Cantidad == 0);
            int denominacionesAgotadas = estado.Dinero.Count(d => d.Cantidad == 0);
            
            txtEstado.Text = $"Productos: {estado.Productos.Count - productosAgotados}/{estado.Productos.Count} disponibles | " +
                           $"Denominaciones: {estado.Dinero.Count - denominacionesAgotadas}/{estado.Dinero.Count} disponibles";
        }
    }

    // Clases para la máquina expendedora
    public class Producto
    {
        public int Id { get; set; }
        public string Nombre { get; set; }
        public decimal Precio { get; set; }
        public int Cantidad { get; set; }
    }

    public class DineroEnMaquina
    {
        public decimal Denominacion { get; set; }
        public int Cantidad { get; set; }
    }

    public class EstadoMaquina
    {
        public List<Producto> Productos { get; set; }
        public List<DineroEnMaquina> Dinero { get; set; }
    }

    public class DatabaseManager
    {
        private string connectionString = "Data Source=maquina_expendedora.db;Version=3;";

        public void InicializarBaseDeDatos()
        {
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();

                // Tabla de productos
                var command = connection.CreateCommand();
                command.CommandText = @"
                    CREATE TABLE IF NOT EXISTS Productos (
                        Id INTEGER PRIMARY KEY AUTOINCREMENT,
                        Nombre TEXT NOT NULL,
                        Precio DECIMAL NOT NULL,
                        Cantidad INTEGER NOT NULL
                    )";
                command.ExecuteNonQuery();

                // Tabla de dinero
                command.CommandText = @"
                    CREATE TABLE IF NOT EXISTS Dinero (
                        Denominacion DECIMAL PRIMARY KEY,
                        Cantidad INTEGER NOT NULL
                    )";
                command.ExecuteNonQuery();

                // Insertar productos iniciales si no existen
                command.CommandText = "SELECT COUNT(*) FROM Productos";
                var count = Convert.ToInt64(command.ExecuteScalar());
                
                if (count == 0)
                {
                    // 9 productos en lugar de 6
                    var productos = new[]
                    {
                        ("Refresco", 15.50m), ("Galletas", 10.00m), ("Papas", 12.00m),
                        ("Chocolate", 8.00m), ("Jugo", 18.00m), ("Agua", 10.00m),
                        ("Sandwich", 25.00m), ("Café", 14.00m), ("Pastelito", 22.00m) // 3 productos nuevos
                    };

                    foreach (var (nombre, precio) in productos)
                    {
                        command.CommandText = "INSERT INTO Productos (Nombre, Precio, Cantidad) VALUES (@nombre, @precio, 10)";
                        command.Parameters.AddWithValue("@nombre", nombre);
                        command.Parameters.AddWithValue("@precio", precio);
                        command.ExecuteNonQuery();
                    }

                    // Insertar denominaciones iniciales
                    decimal[] denominaciones = { 0.50m, 1, 2, 5, 20, 50, 100, 200 };
                    foreach (var denom in denominaciones)
                    {
                        command.CommandText = "INSERT INTO Dinero (Denominacion, Cantidad) VALUES (@denom, 10)";
                        command.Parameters.AddWithValue("@denom", denom);
                        command.ExecuteNonQuery();
                    }
                }
            }
        }

        public List<Producto> ObtenerProductos()
        {
            var productos = new List<Producto>();
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                var command = connection.CreateCommand();
                command.CommandText = "SELECT * FROM Productos";
                
                using (var reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        productos.Add(new Producto
                        {
                            Id = reader.GetInt32(0),
                            Nombre = reader.GetString(1),
                            Precio = reader.GetDecimal(2),
                            Cantidad = reader.GetInt32(3)
                        });
                    }
                }
            }
            return productos;
        }

        public (bool exitoso, string mensaje) RealizarCompra(int productoId, Dictionary<decimal, int> dineroIngresado, decimal cambioNecesario)
        {
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                using (var transaction = connection.BeginTransaction())
                {
                    try
                    {
                        var command = connection.CreateCommand();
                        
                        // Verificar stock
                        command.CommandText = "SELECT Cantidad FROM Productos WHERE Id = @id";
                        command.Parameters.AddWithValue("@id", productoId);
                        var stock = Convert.ToInt32(command.ExecuteScalar());
                        
                        if (stock <= 0)
                            return (false, "Producto agotado");

                        // Actualizar stock
                        command.CommandText = "UPDATE Productos SET Cantidad = Cantidad - 1 WHERE Id = @id";
                        command.Parameters.AddWithValue("@id", productoId);
                        command.ExecuteNonQuery();

                        // Agregar dinero ingresado
                        foreach (var (denominacion, cantidad) in dineroIngresado)
                        {
                            if (cantidad > 0)
                            {
                                command.CommandText = "UPDATE Dinero SET Cantidad = Cantidad + @cantidad WHERE Denominacion = @denom";
                                command.Parameters.AddWithValue("@cantidad", cantidad);
                                command.Parameters.AddWithValue("@denom", denominacion);
                                command.ExecuteNonQuery();
                            }
                        }

                        // Dar cambio si es necesario
                        if (cambioNecesario > 0)
                        {
                            var cambioDesglosado = CalcularCambioExacto(cambioNecesario, connection);
                            if (cambioDesglosado == null)
                                return (false, "Error al calcular el cambio");

                            foreach (var (denominacion, cantidad) in cambioDesglosado)
                            {
                                command.CommandText = "UPDATE Dinero SET Cantidad = Cantidad - @cantidad WHERE Denominacion = @denom";
                                command.Parameters.AddWithValue("@cantidad", cantidad);
                                command.Parameters.AddWithValue("@denom", denominacion);
                                command.ExecuteNonQuery();
                            }
                        }

                        transaction.Commit();
                        return (true, "Compra exitosa");
                    }
                    catch (Exception ex)
                    {
                        transaction.Rollback();
                        return (false, ex.Message);
                    }
                }
            }
        }

        public (bool esPosible, Dictionary<decimal, int> cambio) CalcularCambioDisponible(decimal monto)
        {
            var cambio = CalcularCambioExacto(monto);
            return (cambio != null, cambio);
        }

        private Dictionary<decimal, int> CalcularCambioExacto(decimal monto, SQLiteConnection connection = null)
        {
            bool shouldClose = false;
            if (connection == null)
            {
                connection = new SQLiteConnection(connectionString);
                connection.Open();
                shouldClose = true;
            }

            try
            {
                var command = connection.CreateCommand();
                var cambio = new Dictionary<decimal, int>();
                decimal restante = monto;

                // Ordenar denominaciones de mayor a menor
                command.CommandText = "SELECT Denominacion, Cantidad FROM Dinero ORDER BY Denominacion DESC";
                using (var reader = command.ExecuteReader())
                {
                    var denominaciones = new List<(decimal denom, int cantidad)>();
                    while (reader.Read())
                    {
                        denominaciones.Add((reader.GetDecimal(0), reader.GetInt32(1)));
                    }

                    foreach (var (denominacion, cantidadDisponible) in denominaciones)
                    {
                        if (restante == 0) break;
                        if (denominacion > restante) continue;

                        int cantidadNecesaria = (int)(restante / denominacion);
                        int cantidadUsar = Math.Min(cantidadNecesaria, cantidadDisponible);
                        
                        if (cantidadUsar > 0)
                        {
                            cambio[denominacion] = cantidadUsar;
                            restante -= cantidadUsar * denominacion;
                            // Evitar errores de punto flotante
                            restante = Math.Round(restante, 2);
                        }
                    }
                }

                return restante == 0 ? cambio : null;
            }
            finally
            {
                if (shouldClose)
                    connection.Close();
            }
        }

        public EstadoMaquina ObtenerEstadoMaquina()
        {
            return new EstadoMaquina
            {
                Productos = ObtenerProductos(),
                Dinero = ObtenerDineroDisponible()
            };
        }

        private List<DineroEnMaquina> ObtenerDineroDisponible()
        {
            var dinero = new List<DineroEnMaquina>();
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                var command = connection.CreateCommand();
                command.CommandText = "SELECT * FROM Dinero ORDER BY Denominacion";
                
                using (var reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        dinero.Add(new DineroEnMaquina
                        {
                            Denominacion = reader.GetDecimal(0),
                            Cantidad = reader.GetInt32(1)
                        });
                    }
                }
            }
            return dinero;
        }

        public void RecargarProductos()
        {
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                var command = connection.CreateCommand();
                command.CommandText = "UPDATE Productos SET Cantidad = 10";
                command.ExecuteNonQuery();
            }
        }

        public void RecargarDinero()
        {
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                var command = connection.CreateCommand();
                command.CommandText = "UPDATE Dinero SET Cantidad = 10";
                command.ExecuteNonQuery();
            }
        }

        public (bool exitoso, string mensaje, int cantidadRecargada) RecargarDenominacionIndividual(decimal denominacion, int cantidadDeseada)
        {
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                using (var transaction = connection.BeginTransaction())
                {
                    try
                    {
                        var command = connection.CreateCommand();
                        
                        // Obtener cantidad actual
                        command.CommandText = "SELECT Cantidad FROM Dinero WHERE Denominacion = @denom";
                        command.Parameters.AddWithValue("@denom", denominacion);
                        var cantidadActual = Convert.ToInt32(command.ExecuteScalar());
                        
                        // Calcular cuántos podemos agregar (máximo 10 en total)
                        int espacioDisponible = 10 - cantidadActual;
                        int cantidadAAgregar = Math.Min(cantidadDeseada, espacioDisponible);
                        
                        if (cantidadAAgregar <= 0)
                        {
                            return (false, $"Ya hay {cantidadActual} unidades de ${denominacion}. No se puede agregar más.", 0);
                        }
                        
                        // Actualizar la cantidad
                        command.CommandText = "UPDATE Dinero SET Cantidad = Cantidad + @cantidad WHERE Denominacion = @denom";
                        command.Parameters.AddWithValue("@cantidad", cantidadAAgregar);
                        command.Parameters.AddWithValue("@denom", denominacion);
                        command.ExecuteNonQuery();
                        
                        transaction.Commit();
                        
                        string mensaje = cantidadAAgregar == cantidadDeseada 
                            ? $"✅ Recargados {cantidadAAgregar} de ${denominacion}"
                            : $"✅ Recargados {cantidadAAgregar} de ${denominacion} (solo había espacio para {cantidadAAgregar} de {cantidadDeseada} solicitados)";
                        
                        return (true, mensaje, cantidadAAgregar);
                    }
                    catch (Exception ex)
                    {
                        transaction.Rollback();
                        return (false, $"Error: {ex.Message}", 0);
                    }
                }
            }
        }

        public Dictionary<decimal, int> ObtenerEstadoDenominaciones()
        {
            var estado = new Dictionary<decimal, int>();
            using (var connection = new SQLiteConnection(connectionString))
            {
                connection.Open();
                var command = connection.CreateCommand();
                command.CommandText = "SELECT Denominacion, Cantidad FROM Dinero ORDER BY Denominacion";
                
                using (var reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        estado[reader.GetDecimal(0)] = reader.GetInt32(1);
                    }
                }
            }
            return estado;
        }
    }
}