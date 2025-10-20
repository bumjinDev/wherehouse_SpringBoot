export const LocationAPI = {
    async analyzeLocation(latitude, longitude, radius = 500) {
        try {
            const response = await fetch('/wherehouse/api/location-analysis', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    latitude: latitude,
                    longitude: longitude,
                    radius: radius
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.analysis_status !== 'SUCCESS') {
                throw new Error('Analysis failed');
            }

            return data;
        } catch (error) {
            console.error('Location analysis error:', error);
            throw error;
        }
    }
};