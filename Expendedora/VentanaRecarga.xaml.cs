using System.Windows;

namespace MyWpfApp
{
    public partial class VentanaRecarga : Window
    {
        private DatabaseManager dbManager;

        public VentanaRecarga(DatabaseManager dbManager)
        {
            InitializeComponent();
            this.dbManager = dbManager;
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
        }

        private void BtnRecargarTodo_Click(object sender, RoutedEventArgs e)
        {
            dbManager.RecargarProductos();
            dbManager.RecargarDinero();
            txtMensaje.Text = "✅ Máquina completamente recargada";
        }
    }
}