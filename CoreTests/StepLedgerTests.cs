using System;
using StepQuest.Core.Steps;
using Xunit;

namespace StepQuest.Tests
{
    public class StepLedgerTests
    {
        static readonly DateTime T0 = new DateTime(2026, 3, 2, 8, 0, 0, DateTimeKind.Utc);

        [Fact]
        public void EveryThousandStepsIsOneAction_RemainderCarries()
        {
            var l = new StepLedger();
            Assert.Equal(0, l.AddSteps(999, T0, T0.AddHours(1), T0.Date));
            Assert.Equal(999, l.StepRemainder);
            Assert.Equal(1, l.AddSteps(1, T0.AddHours(1), T0.AddHours(2), T0.Date));
            Assert.Equal(0, l.StepRemainder);
            Assert.Equal(2, l.AddSteps(2500, T0.AddHours(2), T0.AddHours(3), T0.Date));
            Assert.Equal(500, l.StepRemainder);
            Assert.Equal(3, l.BankedActions);
            Assert.Equal(3500, l.TotalSteps);
            Assert.Equal(500, l.StepsToNextAction);
        }

        [Fact]
        public void SyncIsIdempotentForOldRanges()
        {
            var l = new StepLedger();
            l.AddSteps(5000, T0, T0.AddHours(1), T0.Date);
            Assert.Equal(0, l.AddSteps(5000, T0, T0.AddHours(1), T0.Date));
            Assert.Equal(5000, l.TotalSteps);
        }

        [Fact]
        public void ImplausibleBurstsAreCapped()
        {
            var l = new StepLedger();
            l.AddSteps(1_000_000, T0, T0.AddMinutes(10), T0.Date);
            Assert.Equal(10 * StepLedger.MaxStepsPerMinute, l.TotalSteps);
        }

        [Fact]
        public void RawSensor_FirstReadingIsBaseline_ThenDeltas_RebootResets()
        {
            var l = new StepLedger();
            Assert.Equal(0, l.AddRawSensorReading(50_000, T0, T0.Date));
            Assert.Equal(0, l.TotalSteps);
            l.AddRawSensorReading(51_200, T0, T0.Date);
            Assert.Equal(1200, l.TotalSteps);
            // Reboot: counter restarts near zero.
            l.AddRawSensorReading(300, T0, T0.Date);
            Assert.Equal(1500, l.TotalSteps);
            Assert.Equal(1, l.BankedActions);
        }

        [Fact]
        public void SpendingActions()
        {
            var l = new StepLedger();
            Assert.False(l.TrySpendAction());
            l.AddSteps(2000, T0, T0.AddHours(1), T0.Date);
            Assert.True(l.TrySpendAction());
            Assert.True(l.TrySpendAction());
            Assert.False(l.TrySpendAction());
            Assert.Equal(2, l.TotalActionsEarned);
        }

        [Fact]
        public void DailyBuckets()
        {
            var l = new StepLedger();
            l.AddSteps(3000, T0, T0.AddHours(1), new DateTime(2026, 3, 2));
            l.AddSteps(4000, T0.AddDays(1), T0.AddDays(1).AddHours(1), new DateTime(2026, 3, 3));
            Assert.Equal(3000, l.StepsOn(new DateTime(2026, 3, 2)));
            Assert.Equal(4000, l.StepsOn(new DateTime(2026, 3, 3)));
            Assert.Equal(0, l.StepsOn(new DateTime(2026, 3, 4)));
        }

        [Theory]
        [InlineData(180, 0.747)]
        [InlineData(160, 0.664)]
        [InlineData(0, DistanceCalc.DefaultStrideMeters)]
        public void StrideFromHeight(int cm, double expected)
        {
            Assert.Equal(expected, DistanceCalc.StrideFromHeightCm(cm), 3);
        }
    }
}
