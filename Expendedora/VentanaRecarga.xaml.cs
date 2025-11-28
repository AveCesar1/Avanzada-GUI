using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media; // ¡Agregar este using!

namespace MyWpfApp
{
    public partial class VentanaRecarga : Window
    {
        private DatabaseManager dbManager;

        public VentanaRecarga(DatabaseManager dbManager)
        {
            InitializeComponent();
            this.dbManager = dbManager;
            CargarDenominaciones();
        }

        private void CargarDenominaciones()
        {
            var estado = dbManager.ObtenerEstadoDenominaciones();
            var denominaciones = new[]
            {
                0.50m, 1, 2, 5, 20, 50, 100, 200
            };

            var items = denominaciones.Select(denom => new
            {
                Denominacion = denom,
                DenominacionText = $"${denom}",
                EstadoText = $"Actual: {estado[denom]}/10 disponibles"
            }).ToList();

            DenominacionesContainer.ItemsSource = items;
        }

        private void BtnRecargarProductos_Click(object sender, RoutedEventArgs e)
        {
            dbManager.RecargarProductos();
            txtMensaje.Text = "✅ Productos recargados exitosamente";
        }

        private void BtnRecargarDinero_Click(object sender, RoutedEventArgs e)
        {
            dbManager.RecargarDinero();
            txtMensaje.Text = "✅ Dinero recargado exitosamente";
            CargarDenominaciones(); // Actualizar la vista
        }

        private void BtnRecargarTodo_Click(object sender, RoutedEventArgs e)
        {
            dbManager.RecargarProductos();
            dbManager.RecargarDinero();
            txtMensaje.Text = "✅ Máquina completamente recargada";
            CargarDenominaciones(); // Actualizar la vista
        }

        private void BtnRecargarIndividual_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button button && button.Tag is decimal denominacion)
            {
                // Encontrar el TextBox correspondiente
                var container = DenominacionesContainer;
                var item = FindVisualParent<Border>(button);
                if (item != null)
                {
                    var textBox = FindVisualChild<TextBox>(item);
                    if (textBox != null && int.TryParse(textBox.Text, out int cantidad) && cantidad > 0)
                    {
                        var resultado = dbManager.RecargarDenominacionIndividual(denominacion, cantidad);
                        txtMensaje.Text = resultado.mensaje;
                        
                        if (resultado.exitoso)
                        {
                            CargarDenominaciones(); // Actualizar la vista
                        }
                    }
                    else
                    {
                        txtMensaje.Text = "❌ Por favor ingrese una cantidad válida (número mayor a 0)";
                    }
                }
            }
        }

        private void TxtCantidad_TextChanged(object sender, TextChangedEventArgs e)
        {
            if (sender is TextBox textBox)
            {
                // Validar que solo sean números
                if (!string.IsNullOrEmpty(textBox.Text) && !int.TryParse(textBox.Text, out _))
                {
                    // Si no es un número, revertir al último valor válido
                    int cursorPosition = textBox.SelectionStart;
                    string newText = new string(textBox.Text.Where(char.IsDigit).ToArray());
                    if (string.IsNullOrEmpty(newText)) newText = "1";
                    textBox.Text = newText;
                    textBox.SelectionStart = Math.Min(cursorPosition - 1, newText.Length);
                }
            }
        }

        // Métodos auxiliares para encontrar controles en el árbol visual
        private static T FindVisualParent<T>(DependencyObject child) where T : DependencyObject
        {
            while (child != null && !(child is T))
            {
                child = VisualTreeHelper.GetParent(child);
            }
            return child as T;
        }

        private static T FindVisualChild<T>(DependencyObject parent) where T : DependencyObject
        {
            for (int i = 0; i < VisualTreeHelper.GetChildrenCount(parent); i++)
            {
                var child = VisualTreeHelper.GetChild(parent, i);
                if (child is T result)
                {
                    return result;
                }
                else
                {
                    var descendant = FindVisualChild<T>(child);
                    if (descendant != null)
                    {
                        return descendant;
                    }
                }
            }
            return null;
        }
    }
}