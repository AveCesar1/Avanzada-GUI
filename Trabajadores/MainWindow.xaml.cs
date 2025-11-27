using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;

namespace MyWpfApp
{
    public partial class MainWindow : Window
    {
        // Lista de empleados
        private ObservableCollection<Empleado> empleados = new ObservableCollection<Empleado>();
        // Índice actual para navegación
        private int indiceActual = -1;

        public MainWindow()
        {
            InitializeComponent();
            dgEmpleados.ItemsSource = empleados;
        }

        // Struct para empleado (como solicitaste)
        public struct Empleado
        {
            public string Nombre { get; set; }
            public string Puesto { get; set; }
            public string Departamento { get; set; }
        }

        // Evento del botón Agregar
        private void BtnAgregar_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrWhiteSpace(txtNombre.Text) || 
                string.IsNullOrWhiteSpace(txtPuesto.Text) || 
                string.IsNullOrWhiteSpace(txtDepartamento.Text))
            {
                MessageBox.Show("Por favor, complete todos los campos.", "Campos incompletos", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            Empleado nuevoEmpleado = new Empleado
            {
                Nombre = txtNombre.Text,
                Puesto = txtPuesto.Text,
                Departamento = txtDepartamento.Text
            };

            empleados.Add(nuevoEmpleado);
            BtnLimpiar_Click(sender, e); // Limpiar campos después de agregar
        }

        // Evento del botón Limpiar
        private void BtnLimpiar_Click(object sender, RoutedEventArgs e)
        {
            txtNombre.Clear();
            txtPuesto.Clear();
            txtDepartamento.Clear();
        }

        // Evento del botón Eliminar
        private void BtnEliminar_Click(object sender, RoutedEventArgs e)
        {
            if (dgEmpleados.SelectedItem is Empleado empleado)
            {
                empleados.Remove(empleado);
                // Si eliminamos el empleado actual, resetear el índice
                if (indiceActual >= empleados.Count)
                {
                    indiceActual = -1;
                }
            }
            else
            {
                MessageBox.Show("Por favor, seleccione un empleado para eliminar.", "Seleccione un empleado", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }

        // Evento del botón Regresar
        private void BtnRegresar_Click(object sender, RoutedEventArgs e)
        {
            if (empleados.Count == 0) return;

            if (indiceActual <= 0)
            {
                indiceActual = empleados.Count - 1; // Ir al último
            }
            else
            {
                indiceActual--;
            }

            MostrarEmpleadoActual();
        }

        // Evento del botón Adelantar
        private void BtnAdelantar_Click(object sender, RoutedEventArgs e)
        {
            if (empleados.Count == 0) return;

            if (indiceActual >= empleados.Count - 1)
            {
                indiceActual = 0; // Volver al primero
            }
            else
            {
                indiceActual++;
            }

            MostrarEmpleadoActual();
        }

        // Método para mostrar el empleado actual en los cuadros de texto
        private void MostrarEmpleadoActual()
        {
            if (indiceActual >= 0 && indiceActual < empleados.Count)
            {
                Empleado emp = empleados[indiceActual];
                txtNombre.Text = emp.Nombre;
                txtPuesto.Text = emp.Puesto;
                txtDepartamento.Text = emp.Departamento;
            }
        }

        // Evento cuando se selecciona un empleado en el DataGrid
        private void DgEmpleados_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (dgEmpleados.SelectedIndex >= 0)
            {
                indiceActual = dgEmpleados.SelectedIndex;
                MostrarEmpleadoActual();
            }
        }
    }
}